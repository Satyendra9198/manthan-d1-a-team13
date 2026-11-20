package com.returnright.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

/** Part 2 §12 / build plan S2: one payout per Idempotency-Key, mismatch -> 422, admin auth, fault profiles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.admin-token=" + TestSecrets.GATEWAY_ADMIN_TOKEN,
        "spring.datasource.url=jdbc:h2:mem:gateway-test;DB_CLOSE_DELAY=-1",
        "gateway.faults.latency-ms-min=0",
        "gateway.faults.latency-ms-max=5",
        "gateway.faults.timeout-sleep-ms=300",
})
class GatewayIdempotencyTest {

    private static final String ADMIN = TestSecrets.GATEWAY_ADMIN_TOKEN;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void healthy() throws Exception {
        send("POST", "/admin/faults/profile/healthy", null, Map.of("X-Admin-Token", ADMIN));
    }

    @Test
    void fiftyConcurrentRequestsWithOneKeyMakeOnePayout() throws Exception {
        String key = "rr-" + UUID.randomUUID() + "-1";
        List<Res> results = concurrently(50, () -> refund(key, "pay_CARD_1", 149_900));

        assertThat(results).extracting(Res::status).allMatch(s -> s == 200 || s == 201);
        assertThat(results).filteredOn(r -> r.status() == 201).hasSize(1);   // exactly one created it
        Set<Object> ids = results.stream().map(r -> r.body().get("gateway_refund_id")).collect(Collectors.toSet());
        assertThat(ids).hasSize(1);
        assertThat(payoutsFor(key)).isEqualTo(1);
    }

    @Test
    void manyDifferentKeysInParallelAllSucceedWithoutStalling() throws Exception {
        // Many distinct keys at once: guards against lock/pool starvation under virtual threads (Locust storm).
        List<Res> results = concurrently(60, () -> refund("rr-" + UUID.randomUUID() + "-1", "pay_UPI_2", 99_900));
        assertThat(results).extracting(Res::status).containsOnly(201);
    }

    @Test
    void sameKeyWithADifferentAmountIsRejected() throws Exception {
        String key = "rr-" + UUID.randomUUID() + "-1";
        assertThat(refund(key, "pay_CARD_3", 100_000).status()).isEqualTo(201);

        Res mismatch = refund(key, "pay_CARD_3", 100_001);

        assertThat(mismatch.status()).isEqualTo(422);
        assertThat(mismatch.raw()).contains("IDEMPOTENCY_KEY_MISMATCH");
        assertThat(payoutsFor(key)).isEqualTo(1);
    }

    @Test
    void replayReturnsTheOriginalResultAndLookupFindsIt() throws Exception {
        String key = "rr-" + UUID.randomUUID() + "-1";
        Res first = refund(key, "pay_CARD_4", 50_000);
        Res replay = refund(key, "pay_CARD_4", 50_000);

        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(replay.body().get("gateway_refund_id")).isEqualTo(first.body().get("gateway_refund_id"));
        Res lookup = send("GET", "/v1/refunds/" + key, null, Map.of());
        assertThat(lookup.status()).isEqualTo(200);
        assertThat(lookup.body().get("status")).isEqualTo("SUCCEEDED");
        assertThat(send("GET", "/v1/refunds/rr-unknown-1", null, Map.of()).status()).isEqualTo(404);
    }

    @Test
    void declineReferenceFailsDefinitively() throws Exception {
        Res r = refund("rr-" + UUID.randomUUID() + "-1", "pay_UPI_DEMO103-DECLINE", 120_000);
        assertThat(r.body().get("status")).isEqualTo("FAILED");
        assertThat(r.body().get("decline_code")).isEqualTo("CARD_CLOSED");
    }

    @Test
    void requestValidation() throws Exception {
        assertThat(send("POST", "/v1/refunds", body("pay_X", 100), Map.of()).status()).isEqualTo(400); // no key
        assertThat(refund("rr-" + UUID.randomUUID() + "-1", "pay_X", 0).status()).isEqualTo(422);
    }

    @Test
    void adminEndpointsNeedTheToken() throws Exception {
        assertThat(send("POST", "/admin/faults/profile/blackhole", null, Map.of()).status()).isIn(401, 403);
        assertThat(send("GET", "/admin/faults", null, Map.of("X-Admin-Token", "wrong")).status()).isIn(401, 403);
        assertThat(send("GET", "/admin/faults", null, Map.of("X-Admin-Token", ADMIN)).status()).isEqualTo(200);
    }

    @Test
    void lostResponseProfileStillPaysExactlyOnceAcrossRetries() throws Exception {
        assertThat(send("POST", "/admin/faults/profile/lost-responses", null, Map.of("X-Admin-Token", ADMIN)).status())
                .isEqualTo(200);
        String key = "rr-" + UUID.randomUUID() + "-1";
        for (int i = 0; i < 10; i++) {   // the caller retries with the same key; some responses are delayed
            refund(key, "pay_CARD_5", 75_000);
        }
        assertThat(payoutsFor(key)).isEqualTo(1);
        assertThat(send("GET", "/v1/refunds/" + key, null, Map.of()).body().get("status")).isEqualTo("SUCCEEDED");
    }

    // ----------------------------------------------------------------------------------------------- helpers

    record Res(int status, Map<String, Object> body, String raw, java.net.http.HttpHeaders headers) {}

    private Res refund(String key, String paymentReference, long amountPaise) throws Exception {
        return send("POST", "/v1/refunds", body(paymentReference, amountPaise), Map.of("Idempotency-Key", key));
    }

    private static Map<String, Object> body(String paymentReference, long amountPaise) {
        return Map.of("payment_reference", paymentReference, "amount_paise", amountPaise, "currency", "INR",
                "merchant_reference", "refund:test");
    }

    /** Rows the gateway holds for this key, from the admin listing. */
    @SuppressWarnings("unchecked")
    private long payoutsFor(String key) throws Exception {
        Res all = send("GET", "/v1/refunds?limit=5000", null, Map.of());
        List<Map<String, Object>> items = (List<Map<String, Object>>) all.body().get("items");
        return items.stream().filter(i -> key.equals(i.get("idempotency_key"))).count();
    }

    private List<Res> concurrently(int n, Callable<Res> call) throws Exception {
        List<Res> out = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Res>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                fs.add(pool.submit(call));
            }
            for (Future<Res> f : fs) {
                out.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Res send(String method, String path, Object body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        headers.forEach(b::header);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String raw = res.body();
        Map<String, Object> parsed = raw == null || !raw.trim().startsWith("{") ? Map.of() : json.readValue(raw, Map.class);
        return new Res(res.statusCode(), parsed, raw, res.headers());
    }
}
