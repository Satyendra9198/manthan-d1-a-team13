package com.returnright.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-001 refund scenarios end to end: real HTTP API, real PostgreSQL (least-privilege rr_app role), seeded golden
 * data, and an in-process fake gateway whose faults we control. The invariant everywhere: one payout per refund.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.web-application-type=servlet",
        "seed.customers=30",
        "seed.orders=60",
        "rr.jobs.enabled=false",      // no background reconciler racing the assertions
        "rr.gateway.timeout-ms=500",
        "rr.security.lookup-per-minute=100",
})
@ActiveProfiles("seed")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefundIdempotencyIntegrationTest {

    private static final String PASSWORD = "ReturnRight#2026";
    private static final String EDGE_EMAIL = "edge.customer@example.com";

    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("returnright").withUsername("rr_owner").withPassword("rr_owner_dev_pw")
            .withCopyFileToContainer(MountableFile.forHostPath("../infra/postgres/init/01-roles.sql"),
                    "/docker-entrypoint-initdb.d/01-roles.sql");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static final FakeGateway GATEWAY = FakeGateway.start();

    static {
        PG.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "rr_app");       // what the app runs as in compose
        r.add("spring.datasource.password", () -> "rr_app_dev_pw");
        r.add("spring.flyway.user", () -> "rr_owner");
        r.add("spring.flyway.password", () -> "rr_owner_dev_pw");
        r.add("spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        r.add("rr.gateway.url", GATEWAY::url);
    }

    @AfterAll
    static void stopGateway() {
        GATEWAY.stop();
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    // ------------------------------------------------------------------------------------------------- scenarios

    @Test
    @Order(1)
    void doubleClickWithTheSameKeyPaysOnce() throws Exception {
        String fin = staff("finance1");
        Map<String, Object> r = queued(fin, "RR-DEMO-101");
        String key = UUID.randomUUID().toString();

        Res first = approve(fin, r, key);
        Res second = approve(fin, r, key);   // the double click

        assertThat(first.status()).isEqualTo(200);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        Map<String, Object> after = getReturn(fin, id(r));
        assertThat(after.get("status")).isEqualTo("REFUNDED");
        assertThat(attempts(after)).hasSize(1).first().satisfies(a -> assertThat(a.get("status")).isEqualTo("SUCCEEDED"));
        assertThat(GATEWAY.payoutsFor(gatewayKey(after))).isEqualTo(1);
    }

    @Test
    @Order(2)
    void twoReviewersApprovingAtOnceGiveOneWinnerAndOnePayout() throws Exception {   // F-04
        String f1 = staff("finance1");
        String f2 = staff("finance2");
        Map<String, Object> r = queued(f1, "RR-DEMO-104");

        List<Res> results = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Res> a = pool.submit(() -> approve(f1, r, UUID.randomUUID().toString()));
            Future<Res> b = pool.submit(() -> approve(f2, r, UUID.randomUUID().toString()));
            results.add(a.get());
            results.add(b.get());
        }

        assertThat(results).extracting(Res::status).containsExactlyInAnyOrder(200, 409);
        Map<String, Object> after = getReturn(f1, id(r));
        assertThat(after.get("status")).isEqualTo("REFUNDED");
        assertThat(attempts(after)).hasSize(1);
        assertThat(GATEWAY.payoutsFor(gatewayKey(after))).isEqualTo(1);
    }

    @Test
    @Order(3)
    void timeoutLeavesTheAttemptUnknownAndRecheckFinishesItWithoutPayingTwice() throws Exception {   // F-02
        String fin = staff("finance1");
        Map<String, Object> r = toPendingFinance("RR-EDGE-009");

        GATEWAY.mode = Mode.TIMEOUT;     // the gateway pays, but answers after our 500 ms timeout
        approve(fin, r, UUID.randomUUID().toString());
        GATEWAY.mode = Mode.HEALTHY;

        Map<String, Object> pending = getReturn(fin, id(r));
        assertThat(pending.get("status")).isEqualTo("REFUND_PENDING");
        Map<String, Object> attempt = attempts(pending).getFirst();
        assertThat(attempt.get("status")).isEqualTo("UNKNOWN");   // never FAILED on a timeout

        // A human retry while UNKNOWN is refused: it could pay twice.
        Res retry = post(fin, "/finance/returns/" + id(r) + "/refund/retry", Map.of(),
                Map.of("If-Match", etag(pending), "Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(retry.status()).isEqualTo(409);

        Res recheck = post(fin, "/finance/refund-attempts/" + attempt.get("id") + "/recheck", null, Map.of());
        assertThat(recheck.status()).isEqualTo(200);
        Map<String, Object> done = getReturn(fin, id(r));
        assertThat(done.get("status")).isEqualTo("REFUNDED");
        assertThat(attempts(done)).hasSize(1);
        assertThat(GATEWAY.payoutsFor(gatewayKey(done))).isEqualTo(1);
    }

    @Test
    @Order(4)
    void lostResponseIsRecoveredByRecheckWithTheSameKey() throws Exception {   // F-03
        String fin = staff("finance2");
        Map<String, Object> r = toPendingFinance("RR-EDGE-011");

        GATEWAY.mode = Mode.LOST_RESPONSE;   // the gateway pays, then the response is lost (502)
        approve(fin, r, UUID.randomUUID().toString());
        GATEWAY.mode = Mode.HEALTHY;

        Map<String, Object> pending = getReturn(fin, id(r));
        Map<String, Object> attempt = attempts(pending).getFirst();
        assertThat(attempt.get("status")).isEqualTo("UNKNOWN");

        Res recheck = post(fin, "/finance/refund-attempts/" + attempt.get("id") + "/recheck", null, Map.of());
        assertThat(recheck.status()).as(recheck.raw()).isEqualTo(200);
        Map<String, Object> done = getReturn(fin, id(r));
        assertThat(done.get("status")).isEqualTo("REFUNDED");
        assertThat(GATEWAY.payoutsFor(gatewayKey(done))).isEqualTo(1);
        assertThat(GATEWAY.postsFor(gatewayKey(done))).as("recheck looks up, never re-posts").isEqualTo(1);
    }

    @Test
    @Order(5)
    void appRoleCannotRewriteTheLedgerOrAuditLog() throws SQLException {   // ledger rules, V3 grants
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), "rr_app", "rr_app_dev_pw")) {
            for (String sql : List.of("UPDATE ledger_entries SET amount_paise = amount_paise",
                    "DELETE FROM ledger_transactions", "UPDATE audit_log SET event = event")) {
                assertThatThrownBy(() -> c.createStatement().executeUpdate(sql))
                        .as(sql).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @Order(6)
    void reconciliationIsCleanAfterEveryScenario() throws Exception {   // R1-R5 = 0
        Res rec = get(staff("manager1"), "/ops/reconciliation");
        assertThat(rec.status()).isEqualTo(200);
        assertThat(rec.body()).as(rec.raw()).containsEntry("ok", true);
    }

    // ------------------------------------------------------------------------------------------------- flow helpers

    /** Customer submits every eligible unit, warehouse scans and grades A, completes -> PENDING_FINANCE (> Rs 10k). */
    private Map<String, Object> toPendingFinance(String orderNumber) throws Exception {
        Res lookup = post(null, "/auth/customer/lookup", Map.of("order_number", orderNumber, "email", EDGE_EMAIL), Map.of());
        assertThat(lookup.status()).as(lookup.raw()).isEqualTo(200);
        String cust = (String) lookup.body().get("access_token");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> u : list(get(cust, "/customer/orders/" + orderNumber).body().get("units"))) {
            if (Boolean.TRUE.equals(map(u.get("eligibility")).get("eligible"))) {
                items.add(Map.of("order_item_id", u.get("order_item_id"), "unit_index", u.get("unit_index"),
                        "reason", "DEFECTIVE"));
            }
        }
        assertThat(items).as("eligible units on " + orderNumber).isNotEmpty();
        Res submit = post(cust, "/customer/returns", Map.of("order_number", orderNumber,
                "refund_method", "ORIGINAL_PAYMENT", "items", items), Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(submit.status()).as(submit.raw()).isEqualTo(201);
        String returnId = id(submit.body());
        String qr = (String) get(cust, "/customer/returns/" + returnId + "/label").body().get("qr_token");

        String wh = staff("warehouse1");
        Map<String, Object> r = map(post(wh, "/warehouse/scan", Map.of("qr_token", qr), Map.of()).body().get("return"));
        for (Map<String, Object> item : list(r.get("items"))) {
            Res inspected = put(wh, "/warehouse/returns/" + returnId + "/items/" + item.get("id") + "/inspection",
                    Map.of("decision", "ACCEPT", "grade", "A"), Map.of("If-Match", etag(r)));
            assertThat(inspected.status()).as(inspected.raw()).isEqualTo(200);
            r = inspected.body();
        }
        Res complete = post(wh, "/warehouse/returns/" + returnId + "/complete-inspection", null,
                Map.of("If-Match", etag(r), "Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(complete.body().get("status")).as(complete.raw()).isEqualTo("PENDING_FINANCE");
        return complete.body();
    }

    /** A seeded return already waiting in the finance queue. */
    private Map<String, Object> queued(String token, String orderNumber) throws Exception {
        Res q = get(token, "/finance/queue?status=PENDING_FINANCE");
        return list(q.body().get("items")).stream().filter(x -> orderNumber.equals(x.get("order_number"))).findFirst()
                .orElseThrow(() -> new AssertionError(orderNumber + " not in finance queue: " + q.raw()));
    }

    private Res approve(String token, Map<String, Object> r, String key) throws Exception {
        return post(token, "/finance/returns/" + id(r) + "/approve", Map.of("note", "ok"),
                Map.of("If-Match", etag(r), "Idempotency-Key", key));
    }

    private Map<String, Object> getReturn(String token, String id) throws Exception {
        return get(token, "/finance/returns/" + id).body();
    }

    private String staff(String username) throws Exception {
        Res r = post(null, "/auth/staff/login", Map.of("username", username, "password", PASSWORD), Map.of());
        assertThat(r.status()).as(r.raw()).isEqualTo(200);
        return (String) r.body().get("access_token");
    }

    private static List<Map<String, Object>> attempts(Map<String, Object> r) {
        return list(map(r.get("refund")).get("attempts"));
    }

    private static String gatewayKey(Map<String, Object> r) {
        return (String) attempts(r).getFirst().get("gateway_idempotency_key");
    }

    private static String id(Map<String, Object> r) {
        return (String) r.get("id");
    }

    private static String etag(Map<String, Object> r) {
        return "\"" + r.get("version") + "\"";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    // ------------------------------------------------------------------------------------------------- HTTP

    record Res(int status, Map<String, Object> body, String raw, java.net.http.HttpHeaders headers) {}

    private Res get(String token, String path) throws Exception {
        return send("GET", token, path, null, Map.of());
    }

    private Res post(String token, String path, Object body, Map<String, String> headers) throws Exception {
        return send("POST", token, path, body, headers);
    }

    private Res put(String token, String path, Object body, Map<String, String> headers) throws Exception {
        return send("PUT", token, path, body, headers);
    }

    @SuppressWarnings("unchecked")
    private Res send(String method, String token, String path, Object body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        headers.forEach(b::header);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String raw = res.body();
        Map<String, Object> parsed = raw == null || raw.isBlank() || !raw.trim().startsWith("{") ? Map.of()
                : json.readValue(raw, Map.class);
        return new Res(res.statusCode(), parsed, method + " " + path + " -> " + res.statusCode() + " " + raw, res.headers());
    }

    // ------------------------------------------------------------------------------------------------- fake gateway

    enum Mode { HEALTHY, TIMEOUT, LOST_RESPONSE }

    /** Minimal stand-in for the mock gateway (Part 2 §12): idempotent by key, with injectable faults. */
    static final class FakeGateway {

        private final HttpServer server;
        private final Map<String, Map<String, Object>> refunds = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> payouts = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> posts = new ConcurrentHashMap<>();
        private final JsonMapper json = JsonMapper.builder().build();
        volatile Mode mode = Mode.HEALTHY;

        private FakeGateway(HttpServer server) {
            this.server = server;
        }

        static FakeGateway start() {
            try {
                HttpServer s = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
                FakeGateway g = new FakeGateway(s);
                s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
                s.createContext("/", g::handle);
                s.start();
                return g;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        int payoutsFor(String key) {
            return payouts.getOrDefault(key, new AtomicInteger()).get();
        }

        int postsFor(String key) {
            return posts.getOrDefault(key, new AtomicInteger()).get();
        }

        @SuppressWarnings("unchecked")
        private void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            try {
                if ("POST".equals(method) && "/v1/refunds".equals(path)) {
                    String key = ex.getRequestHeaders().getFirst("Idempotency-Key");
                    Map<String, Object> req = json.readValue(ex.getRequestBody().readAllBytes(), Map.class);
                    posts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                    Map<String, Object> rec = refunds.computeIfAbsent(key, k -> {
                        boolean decline = String.valueOf(req.get("payment_reference")).contains("DECLINE");
                        if (!decline) {
                            payouts.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet();
                        }
                        return Map.of("status", decline ? "FAILED" : "SUCCEEDED",
                                "gateway_refund_id", "gw_" + UUID.randomUUID(),
                                "decline_code", decline ? "card_declined" : "",
                                "idempotency_key", k, "amount_paise", req.get("amount_paise"));
                    });
                    switch (mode) {
                        case TIMEOUT -> {
                            sleep(1_500);
                            reply(ex, 200, rec);
                        }
                        case LOST_RESPONSE -> reply(ex, 502, Map.of("error", "upstream connection reset"));
                        case HEALTHY -> reply(ex, 200, rec);
                    }
                } else if ("GET".equals(method) && path.startsWith("/v1/refunds/")) {
                    Map<String, Object> rec = refunds.get(path.substring("/v1/refunds/".length()));
                    reply(ex, rec == null ? 404 : 200, rec == null ? Map.of("error", "not_found") : rec);
                } else if ("GET".equals(method) && "/v1/refunds".equals(path)) {
                    reply(ex, 200, Map.of("items", List.copyOf(refunds.values())));
                } else {
                    reply(ex, 200, Map.of("ok", true));   // /healthz, /admin/* used by the seeder
                }
            } finally {
                ex.close();
            }
        }

        private void reply(HttpExchange ex, int status, Object body) throws IOException {
            byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
        }

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
