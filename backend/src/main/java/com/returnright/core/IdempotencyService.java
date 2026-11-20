package com.returnright.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HTTP-layer idempotency (ADR-001 layer L1). One key per confirm dialog. Same key + same body replays the stored
 * response; same key + different body -> 422 IDEMPOTENCY_KEY_REUSED; still running -> 409 REQUEST_IN_PROGRESS.
 * Failures (ApiException) release the key so the client can correct and retry with it.
 * Safety does not depend on this layer alone: the state machine (L2) and DB constraints (L3) still guard.
 */
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration STALE_IN_PROGRESS = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Json json;
    private final Clock clock;
    private final Metrics metrics;

    public IdempotencyService(JdbcTemplate jdbc, TransactionTemplate tx, Json json, Clock clock, Metrics metrics) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
        this.metrics = metrics;
    }

    public ResponseEntity<?> run(String scope, String key, Object requestBody, String route,
                                 Supplier<ResponseEntity<?>> action) {
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key header is required.");
        }
        String hash = sha256(scope + "|" + json.write(requestBody == null ? Map.of() : requestBody));
        Instant now = clock.instant();

        ResponseEntity<?> replay = tx.execute(s -> claim(scope, key, hash, now, route));
        if (replay != null) {
            return replay;
        }
        ResponseEntity<?> result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            tx.executeWithoutResult(s -> jdbc.update("DELETE FROM idempotency_keys WHERE scope = ? AND idem_key = ?",
                    scope, key));
            throw e;
        }
        String body = result.getBody() == null ? null : json.write(result.getBody());
        tx.executeWithoutResult(s -> jdbc.update("""
                UPDATE idempotency_keys SET status = 'COMPLETED', response_status = ?, response_body = ?::jsonb
                WHERE scope = ? AND idem_key = ?""", result.getStatusCode().value(), body, scope, key));
        return result;
    }

    /** Returns a replay response, or null if this request now owns the key. */
    private ResponseEntity<?> claim(String scope, String key, String hash, Instant now, String route) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys (scope, idem_key, request_hash, status, created_at, expires_at)
                VALUES (?, ?, ?, 'IN_PROGRESS', ?, ?) ON CONFLICT DO NOTHING""",
                scope, key, hash, Timestamp.from(now), Timestamp.from(now.plus(TTL)));
        if (inserted == 1) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT request_hash, status, response_status, response_body::text AS body, created_at
                FROM idempotency_keys WHERE scope = ? AND idem_key = ? FOR UPDATE""", scope, key);
        if (rows.isEmpty()) {
            throw ApiException.conflict("REQUEST_IN_PROGRESS", "This request is still being processed.", Map.of());
        }
        Map<String, Object> row = rows.getFirst();
        if (!hash.equals(row.get("request_hash"))) {
            throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request.", Map.of());
        }
        if ("COMPLETED".equals(row.get("status"))) {
            metrics.idempotentReplay(route);
            Object body = row.get("body");
            return ResponseEntity.status(((Number) row.get("response_status")).intValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotent-Replayed", "true")
                    .body(body == null ? "" : body.toString());
        }
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        if (created.plus(STALE_IN_PROGRESS).isAfter(now)) {
            metrics.conflict("REQUEST_IN_PROGRESS");
            throw new ApiException("REQUEST_IN_PROGRESS", "This request is still being processed.",
                    HttpStatus.CONFLICT, Map.of("retry_after_seconds", 1));
        }
        // stale IN_PROGRESS (crashed request): take over; the state machine still prevents double effects
        jdbc.update("UPDATE idempotency_keys SET created_at = ? WHERE scope = ? AND idem_key = ?",
                Timestamp.from(now), scope, key);
        return null;
    }

    public int purgeExpired() {
        return jdbc.update("DELETE FROM idempotency_keys WHERE expires_at < ?", Timestamp.from(clock.instant()));
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
