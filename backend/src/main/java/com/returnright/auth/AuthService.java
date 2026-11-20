package com.returnright.auth;

import com.returnright.audit.AuditService;
import com.returnright.core.Actor;
import com.returnright.core.ApiException;
import com.returnright.core.KvStore;
import com.returnright.core.Metrics;
import com.returnright.core.RrProperties;
import com.returnright.core.enums.Role;
import com.returnright.orders.Customer;
import com.returnright.orders.Order;
import com.returnright.orders.Repositories.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer lookup (order# + email) and staff login with lockout, rate limiting and revocation (Part 2 §11). */
@Service
public class AuthService {

    public record Principal(String type, long id, String name, String role, String username, String email) {}

    public record Session(String accessToken, long expiresIn, Principal principal) {}

    private final OrderRepository orders;
    private final User.UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final KvStore kv;
    private final AuditService audit;
    private final Metrics metrics;
    private final Clock clock;
    private final RrProperties props;
    private final String dummyHash;

    public AuthService(OrderRepository orders, User.UserRepository users, PasswordEncoder encoder, JwtEncoder jwt,
                       KvStore kv, AuditService audit, Metrics metrics, Clock clock, RrProperties props) {
        this.orders = orders;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.kv = kv;
        this.audit = audit;
        this.metrics = metrics;
        this.clock = clock;
        this.props = props;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString()); // equalises timing for unknown users
    }

    @Transactional(readOnly = true)
    public Session customerLookup(String orderNumber, String email, String clientIp) {
        String on = orderNumber == null ? "" : orderNumber.strip().toUpperCase(Locale.ROOT);
        rateLimit("rl:lookup:ip:" + clientIp, props.security().lookupPerMinute(), Duration.ofMinutes(1),
                "Too many attempts. Please wait a minute and try again.");
        rateLimit("rl:lookup:order:" + on, 10, Duration.ofHours(1),
                "Too many attempts for this order. Please try again in an hour, or contact support.");
        Order o = orders.findByOrderNumber(on).orElse(null);
        Customer c = o == null ? null : o.getCustomer();
        if (c == null || email == null || !c.getEmail().equalsIgnoreCase(email.strip())) {
            metrics.loginFailure("customer_lookup");
            // generic message: never reveal whether the order number or the email was wrong
            throw new ApiException("NOT_FOUND", "We couldn't find an order with those details.", HttpStatus.NOT_FOUND);
        }
        Principal p = new Principal("customer", c.getId(), c.getFullName(), Role.CUSTOMER.name(), null, c.getEmail());
        return issue("customer:" + c.getId(), p, props.jwt().customerTtlMinutes());
    }

    @Transactional
    public Session staffLogin(String username, String password, String clientIp) {
        String u = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        String lockKey = "lock:user:" + u;
        if (kv.hasFlag(lockKey)) {
            metrics.loginFailure("locked");
            throw new ApiException("ACCOUNT_LOCKED", "Too many failed attempts. Try again in %d minutes."
                    .formatted(Math.max(1, kv.ttlSeconds(lockKey) / 60)), HttpStatus.LOCKED);
        }
        rateLimit("rl:login:ip:" + clientIp, 30, Duration.ofMinutes(1),
                "Too many attempts. Please wait a minute and try again.");
        User user = users.findByUsername(u).orElse(null);
        boolean ok = encoder.matches(password == null ? "" : password, user == null ? dummyHash : user.getPasswordHash())
                && user != null && user.isActive();
        if (!ok) {
            metrics.loginFailure("staff");
            long fails = kv.incrementWindow("fail:user:" + u, Duration.ofSeconds(props.security().loginLockoutSeconds()));
            if (fails >= props.security().loginMaxFailures()) {
                kv.setFlag(lockKey, Duration.ofSeconds(props.security().loginLockoutSeconds()));
                kv.delete("fail:user:" + u);
            }
            throw new ApiException("INVALID_CREDENTIALS", "Incorrect username or password.", HttpStatus.UNAUTHORIZED);
        }
        kv.delete("fail:user:" + u);
        user.loggedIn(clock.instant());
        Actor actor = Actor.staff(user.getId(), user.getRole(), user.getDisplayName());
        audit.record(actor, "user", user.getId().toString(), "LOGIN", null, null, null, null, Map.of("ip", clientIp));
        Principal p = new Principal("staff", user.getId(), user.getDisplayName(), user.getRole().name(),
                user.getUsername(), user.getEmail());
        return issue("user:" + user.getId(), p, props.jwt().staffTtlMinutes());
    }

    public void logout(Jwt token) {
        Instant exp = token.getExpiresAt() == null ? clock.instant().plusSeconds(3600) : token.getExpiresAt();
        long ttl = Math.max(1, Duration.between(clock.instant(), exp).getSeconds());
        kv.setFlag("revoked:" + token.getId(), Duration.ofSeconds(ttl));
    }

    private Session issue(String sub, Principal p, int ttlMinutes) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer()).subject(sub).issuedAt(now).notBefore(now.minusSeconds(5))
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes))).id(UUID.randomUUID().toString())
                .claim("role", p.role()).claim("name", p.name()).claim("ptype", p.type());
        if (p.username() != null) {
            claims.claim("username", p.username());
        }
        if (p.email() != null) {
            claims.claim("email", p.email());
        }
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new Session(token, ttlMinutes * 60L, p);
    }

    /** The message names the window that actually tripped (the per-order one is an hour, not a minute). */
    private void rateLimit(String key, int max, Duration window, String message) {
        if (kv.incrementWindow(key, window) > max) {
            throw new ApiException("RATE_LIMITED", message, HttpStatus.TOO_MANY_REQUESTS,
                    java.util.Map.<String, Object>of("retry_after_seconds", Math.max(1, kv.ttlSeconds(key))));
        }
    }

    /** Builds the domain Actor from a validated JWT. */
    public static Actor actorFrom(Jwt jwt) {
        String sub = jwt.getSubject();
        Role role = Role.valueOf(jwt.getClaimAsString("role"));
        String name = jwt.getClaimAsString("name");
        long id = Long.parseLong(sub.substring(sub.indexOf(':') + 1));
        return sub.startsWith("customer:") ? Actor.customer(id, name) : Actor.staff(id, role, name);
    }

    public static Principal principalFrom(Jwt jwt) {
        String sub = jwt.getSubject();
        long id = Long.parseLong(sub.substring(sub.indexOf(':') + 1));
        return new Principal(jwt.getClaimAsString("ptype"), id, jwt.getClaimAsString("name"),
                jwt.getClaimAsString("role"), jwt.getClaimAsString("username"), jwt.getClaimAsString("email"));
    }
}
