package com.returnright.gateway;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock gateway contract (Part 2 §12.1). Same key + same body replays the stored result; same key + different body
 * is 422; concurrent requests with one key are serialised. A payout is decided at most once per key.
 */
@RestController
public class GatewayController {

    public record RefundRequest(@NotBlank @Size(max = 128) String paymentReference, Long amountPaise, String currency,
                                @Size(max = 128) String merchantReference) {}

    private final GatewayRefund.Repo repo;
    private final Faults faults;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    private final String adminToken;
    private final String env;
    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();

    public GatewayController(GatewayRefund.Repo repo, Faults faults, TransactionTemplate tx, MeterRegistry metrics,
                             @Value("${gateway.admin-token}") String adminToken, @Value("${gateway.env}") String env) {
        this.repo = repo;
        this.faults = faults;
        this.tx = tx;
        this.metrics = metrics;
        this.adminToken = adminToken;
        this.env = env;
    }

    @PostMapping("/v1/refunds")
    public ResponseEntity<Map<String, Object>> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                      @Valid @RequestBody RefundRequest req) throws InterruptedException {
        if (key == null || key.isBlank() || key.length() > 128) {
            return error(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required.");
        }
        if (req.amountPaise() == null || req.amountPaise() <= 0 || !"INR".equals(req.currency())) {
            return error(422, "VALIDATION_ERROR", "amount_paise must be positive and currency must be INR.");
        }
        Faults.Config cfg = faults.get();
        Thread.sleep(faults.latencyMs());
        if (faults.roll(cfg.errorRate())) { // 503 BEFORE processing: nothing happened
            fault("error");
            return error(503, "SERVICE_UNAVAILABLE", "Injected fault: gateway unavailable.");
        }
        if (faults.roll(cfg.timeoutRate())) { // hang, then process: the caller has timed out but money moves
            fault("timeout");
            Thread.sleep(cfg.timeoutSleepMs());
        }
        String hash = sha256(req.paymentReference() + "|" + req.amountPaise() + "|" + req.currency());
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        Result result;
        synchronized (lock) {
            result = tx.execute(s -> {
                GatewayRefund existing = repo.findByIdempotencyKey(key).orElse(null);
                if (existing != null) {
                    if (!existing.getRequestHash().equals(hash)) {
                        return new Result(null, false, true);
                    }
                    existing.settleIfDue(Instant.now());
                    return new Result(existing, true, false);
                }
                String status = "SUCCEEDED";
                String decline = null;
                if (req.paymentReference().endsWith("-DECLINE")) {
                    status = "FAILED";
                    decline = "CARD_CLOSED";
                } else if (faults.roll(cfg.declineRate())) {
                    status = "FAILED";
                    decline = "ISSUER_DECLINED";
                    fault("decline");
                } else if (faults.roll(cfg.processingRate())) {
                    status = "PROCESSING";
                    fault("processing");
                }
                Instant now = Instant.now();
                GatewayRefund r = new GatewayRefund(key, hash, "gw_rf_" + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 20), req.paymentReference(), req.amountPaise(), req.currency(),
                        req.merchantReference(), status, decline, now, now.plusSeconds(cfg.settleSeconds()));
                repo.save(r);
                Counter.builder("gateway.refunds").tag("status", status).register(metrics).increment();
                return new Result(r, false, false);
            });
        }
        if (result.mismatch()) {
            return error(422, "IDEMPOTENCY_KEY_MISMATCH", "This Idempotency-Key was used with a different request.");
        }
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body(result.refund()));
        }
        if (faults.roll(cfg.errorAfterCommitRate())) { // committed, but tell the caller it failed
            fault("error_after_commit");
            return error(500, "INTERNAL_ERROR", "Injected fault after commit.");
        }
        if (faults.roll(cfg.dropResponseRate())) { // committed, response lost (client times out)
            fault("drop_response");
            Thread.sleep(cfg.timeoutSleepMs());
        }
        return ResponseEntity.status(201).body(body(result.refund()));
    }

    private record Result(GatewayRefund refund, boolean replayed, boolean mismatch) {}

    @GetMapping("/v1/refunds/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key) throws InterruptedException {
        Thread.sleep(faults.latencyMs());
        GatewayRefund r = tx.execute(s -> repo.findByIdempotencyKey(key).map(x -> {
            x.settleIfDue(Instant.now());
            return x;
        }).orElse(null));
        return r == null ? error(404, "NOT_FOUND", "No refund for this key.") : ResponseEntity.ok(body(r));
    }

    @GetMapping("/v1/refunds")
    public Map<String, Object> list(@RequestParam(required = false) String since,
                                    @RequestParam(defaultValue = "500") int limit) {
        Instant from = since == null ? Instant.EPOCH : Instant.parse(since);
        List<Map<String, Object>> items = repo.findSince(from, PageRequest.of(0, Math.min(limit, 5000))).stream()
                .map(GatewayController::body).toList();
        return Map.of("items", items, "count", items.size());
    }

    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // ------------------------------------------------ admin ------------------------------------------------
    @GetMapping("/admin/faults")
    public ResponseEntity<?> getFaults(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return authorized(token) ? ResponseEntity.ok(faults.describe()) : forbidden();
    }

    @PutMapping("/admin/faults")
    public ResponseEntity<?> putFaults(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                       @RequestBody Map<String, Object> overrides) {
        if (!authorized(token)) {
            return forbidden();
        }
        faults.update(overrides);
        return ResponseEntity.ok(faults.describe());
    }

    @PostMapping("/admin/faults/profile/{name}")
    public ResponseEntity<?> profile(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                     @PathVariable String name) {
        if (!authorized(token)) {
            return forbidden();
        }
        return faults.applyProfile(name) ? ResponseEntity.ok(faults.describe())
                : error(404, "UNKNOWN_PROFILE", "Profiles: healthy, slow, flaky, lost-responses, blackhole, declines, processing");
    }

    @PostMapping("/admin/faults/reset")
    public ResponseEntity<?> reset(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token)) {
            return forbidden();
        }
        faults.reset();
        return ResponseEntity.ok(faults.describe());
    }

    @GetMapping("/admin/refunds")
    public ResponseEntity<?> adminRefunds(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                          @RequestParam(defaultValue = "50") int limit) {
        if (!authorized(token)) {
            return forbidden();
        }
        List<Map<String, Object>> items = repo.latest(PageRequest.of(0, Math.min(limit, 1000))).stream()
                .map(GatewayController::body).toList();
        return ResponseEntity.ok(Map.of("items", items, "total", repo.count()));
    }

    @PostMapping("/admin/reset-data")
    public ResponseEntity<?> resetData(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token) || !"dev".equals(env)) {
            return forbidden();
        }
        tx.executeWithoutResult(s -> repo.deleteAllInBatch());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ------------------------------------------------ helpers ------------------------------------------------
    private boolean authorized(String token) {
        return token != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, Object>> forbidden() {
        return error(403, "FORBIDDEN", "Admin token required.");
    }

    private void fault(String type) {
        Counter.builder("gateway.faults.injected").tag("type", type).register(metrics).increment();
    }

    private static Map<String, Object> body(GatewayRefund r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gateway_refund_id", r.getGatewayRefundId());
        m.put("idempotency_key", r.getIdempotencyKey());
        m.put("status", r.getStatus());
        m.put("decline_code", r.getDeclineCode());
        m.put("amount_paise", r.getAmountPaise());
        m.put("currency", r.getCurrency());
        m.put("payment_reference", r.getPaymentReference());
        m.put("merchant_reference", r.getMerchantReference());
        m.put("created_at", r.getCreatedAt());
        m.put("settled_at", r.getSettledAt());
        return m;
    }

    private static ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message)));
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
