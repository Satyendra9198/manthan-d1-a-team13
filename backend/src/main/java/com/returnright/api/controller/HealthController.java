package com.returnright.api.controller;

import com.returnright.core.infra.KvStore;
import com.returnright.gatewayclient.GatewayClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness and readiness (Part 2 §10.3 platform endpoints). */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final GatewayClient gateway;
    private final KvStore kv;

    public HealthController(JdbcTemplate jdbc, GatewayClient gateway, KvStore kv) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.kv = kv;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readyz() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean db;
        try {
            db = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException e) {
            db = false;
        }
        checks.put("database", db ? "up" : "down");
        checks.put("gateway", gateway.healthy() ? "up" : "down");
        checks.put("redis", kv.ping() ? "up" : "degraded (in-memory fallback)");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", db ? "ready" : "not_ready");
        body.put("checks", checks);
        return ResponseEntity.status(db ? 200 : 503).body(body);
    }
}
