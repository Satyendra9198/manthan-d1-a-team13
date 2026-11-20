package com.returnright.gatewayclient;

import com.returnright.core.Correlation;
import com.returnright.core.Metrics;
import com.returnright.core.RrProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the mock payment gateway (Part 2 §12). Any ambiguity (timeout, 5xx, connection error,
 * malformed body) maps to UNKNOWN — never FAILED (ADR-001 rule 3).
 */
@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    public enum Status { SUCCEEDED, FAILED, PROCESSING, NOT_FOUND, UNKNOWN }

    public record Outcome(Status status, String gatewayRefundId, String declineCode, String detail) {
        static Outcome unknown(String detail) {
            return new Outcome(Status.UNKNOWN, null, null, detail);
        }
    }

    private final RestClient http;
    private final Metrics metrics;

    public GatewayClient(RrProperties props, Metrics metrics) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1000)).build();
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(jdk);
        rf.setReadTimeout(Duration.ofMillis(props.gateway().timeoutMs()));
        this.http = RestClient.builder().baseUrl(props.gateway().url()).requestFactory(rf).build();
        this.metrics = metrics;
    }

    public Outcome createRefund(String idempotencyKey, String paymentReference, long amountPaise, String merchantRef) {
        long t0 = System.nanoTime();
        String outcomeTag = "unknown";
        try {
            Map<String, Object> body = http.post().uri("/v1/refunds")
                    .header("Idempotency-Key", idempotencyKey)
                    .header(Correlation.HEADER, Correlation.id())
                    .body(Map.of("payment_reference", paymentReference, "amount_paise", amountPaise, "currency", "INR",
                            "merchant_reference", merchantRef))
                    .retrieve().body(MAP);
            outcomeTag = "2xx";
            return parse(body);
        } catch (HttpStatusCodeException e) {
            outcomeTag = bucket(e.getStatusCode());
            log.warn("gateway create {} for key {}: {}", e.getStatusCode(), idempotencyKey, e.getResponseBodyAsString());
            return Outcome.unknown("http " + e.getStatusCode().value()); // incl. 422 key mismatch -> human (F-10)
        } catch (RuntimeException e) {
            outcomeTag = isTimeout(e) ? "timeout" : "conn_error";
            log.warn("gateway create {} for key {}: {}", outcomeTag, idempotencyKey, e.toString());
            return Outcome.unknown(outcomeTag);
        } finally {
            metrics.gatewayCall("create", outcomeTag, Duration.ofNanos(System.nanoTime() - t0));
        }
    }

    public Outcome getRefund(String idempotencyKey) {
        long t0 = System.nanoTime();
        String outcomeTag = "unknown";
        try {
            Map<String, Object> body = http.get().uri("/v1/refunds/{key}", idempotencyKey)
                    .header(Correlation.HEADER, Correlation.id()).retrieve().body(MAP);
            outcomeTag = "2xx";
            return parse(body);
        } catch (HttpStatusCodeException e) {
            outcomeTag = bucket(e.getStatusCode());
            if (e.getStatusCode().value() == 404) {
                return new Outcome(Status.NOT_FOUND, null, null, "not found");
            }
            return Outcome.unknown("http " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            outcomeTag = isTimeout(e) ? "timeout" : "conn_error";
            return Outcome.unknown(outcomeTag);
        } finally {
            metrics.gatewayCall("get", outcomeTag, Duration.ofNanos(System.nanoTime() - t0));
        }
    }

    /** For three-way reconciliation R3. */
    public List<Map<String, Object>> listSucceeded() {
        Map<String, Object> body = http.get().uri("/v1/refunds?limit=5000").retrieve().body(MAP);
        Object items = body == null ? null : body.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = items instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        return list;
    }

    public boolean healthy() {
        try {
            http.get().uri("/healthz").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Outcome parse(Map<String, Object> body) {
        if (body == null || body.get("status") == null) {
            return Outcome.unknown("malformed body");
        }
        String s = String.valueOf(body.get("status"));
        String id = body.get("gateway_refund_id") == null ? null : String.valueOf(body.get("gateway_refund_id"));
        String decline = body.get("decline_code") == null ? null : String.valueOf(body.get("decline_code"));
        return switch (s) {
            case "SUCCEEDED" -> new Outcome(Status.SUCCEEDED, id, null, null);
            case "FAILED" -> decline == null ? Outcome.unknown("FAILED without decline_code")
                    : new Outcome(Status.FAILED, id, decline, null);
            case "PROCESSING" -> new Outcome(Status.PROCESSING, id, null, null);
            default -> Outcome.unknown("unexpected status " + s);
        };
    }

    private static String bucket(HttpStatusCode c) {
        return c.is4xxClientError() ? "4xx" : c.is5xxServerError() ? "5xx" : "other";
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.http.HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
