package com.returnright.service.impl;

import com.returnright.config.SecurityConfig;
import com.returnright.config.SecurityProperties;
import com.returnright.exception.InvalidCredentialsException;
import com.returnright.model.dto.request.LoginRequest;
import com.returnright.model.dto.response.LoginResponse;
import com.returnright.model.dto.response.StaffProfileResponse;
import com.returnright.model.entity.StaffUser;
import com.returnright.repository.StaffUserRepository;
import com.returnright.service.AuthService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies staff credentials with BCrypt and issues HS256 tokens. When the email is unknown, a
 * dummy hash is still checked so the response time doesn't reveal whether the account exists.
 */
@Service
public class AuthServiceImpl implements AuthService {

  static final String ISSUER = "returnright";

  /** BCrypt hash of a random value nobody knows; only used to spend the same time. */
  private static final String TIMING_DUMMY_HASH =
      "$2a$10$nDXQQ2WEHV8eNfskDDLY8eO6LFvw6d3cQT3tZv7XtSvebiUtpyTou";

  private final StaffUserRepository staffUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final SecurityProperties securityProperties;
  private final Clock clock;

  public AuthServiceImpl(
      StaffUserRepository staffUserRepository,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      SecurityProperties securityProperties,
      Clock clock) {
    this.staffUserRepository = staffUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.securityProperties = securityProperties;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    Optional<StaffUser> account =
        staffUserRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT));
    String hash = account.map(StaffUser::getPasswordHash).orElse(TIMING_DUMMY_HASH);
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
    if (account.isEmpty() || !passwordMatches || !account.get().isActive()) {
      throw new InvalidCredentialsException();
    }

    StaffUser staff = account.get();
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(securityProperties.tokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(staff.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(SecurityConfig.ROLE_CLAIM, staff.getRole().name())
            .claim("name", staff.getFullName())
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new LoginResponse(token, "Bearer", expiresAt, toProfile(staff));
  }

  @Override
  @Transactional(readOnly = true)
  public StaffProfileResponse currentStaff(UUID staffId) {
    return staffUserRepository
        .findById(staffId)
        .filter(StaffUser::isActive)
        .map(AuthServiceImpl::toProfile)
        .orElseThrow(InvalidCredentialsException::new);
  }

  private static StaffProfileResponse toProfile(StaffUser staff) {
    return new StaffProfileResponse(
        staff.getId(), staff.getEmail(), staff.getFullName(), staff.getRole());
  }
}
