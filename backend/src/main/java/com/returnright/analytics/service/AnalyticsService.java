package com.returnright.analytics.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Manager dashboard (Part 2 §14): computed live with SQL. At hackathon scale every query is a few ms; the upgrade path
 * is materialised views refreshed on a schedule without changing this API.
 */
@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public AnalyticsService(JdbcTemplate jdbc, Clock clock, ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    public Map<String, Object> overview(int days) {
        int window = Math.min(Math.max(days, 1), 365);
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(window));
        Timestamp f = Timestamp.from(from);
        Timestamp t = Timestamp.from(to);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("window_days", window);

        List<Map<String, Object>> byCategory = jdbc.queryForList("""
                SELECT c.code AS category_code, c.name AS category_name,
                       COALESCE(SUM(oi.quantity), 0)::bigint AS units_sold,
                       COALESCE(SUM((SELECT count(*) FROM return_items ri
                                      WHERE ri.order_item_id = oi.id AND ri.status <> 'CANCELLED')), 0)::bigint AS units_returned
                FROM categories c
                LEFT JOIN products p ON p.category_id = c.id
                LEFT JOIN order_items oi ON oi.product_id = p.id
                LEFT JOIN orders o ON o.id = oi.order_id
                WHERE o.status = 'DELIVERED' AND o.delivered_at >= ? AND o.delivered_at < ?
                GROUP BY c.code, c.name ORDER BY c.code""", f, t);
        long sold = 0;
        long returned = 0;
        for (Map<String, Object> row : byCategory) {
            long s = ((Number) row.get("units_sold")).longValue();
            long r = ((Number) row.get("units_returned")).longValue();
            sold += s;
            returned += r;
            row.put("return_rate_pct", s == 0 ? 0.0 : Math.round(10000.0 * r / s) / 100.0);
        }
        byCategory.sort((a, b) -> Double.compare((double) b.get("return_rate_pct"), (double) a.get("return_rate_pct")));
        out.put("return_rate_by_category", byCategory);

        List<Map<String, Object>> reasons = jdbc.queryForList("""
                SELECT ri.reason, count(*)::bigint AS count,
                       round(100.0 * count(*) / NULLIF(sum(count(*)) OVER (), 0), 1)::float8 AS pct
                FROM return_items ri JOIN returns r ON r.id = ri.return_id
                WHERE r.status <> 'CANCELLED' AND r.created_at >= ? AND r.created_at < ?
                GROUP BY ri.reason ORDER BY count(*) DESC""", f, t);
        out.put("top_reasons", reasons);

        Map<String, Object> cycle = jdbc.queryForMap("""
                WITH c AS (
                  SELECT r.id, EXTRACT(EPOCH FROM r.closed_at - r.created_at) / 3600.0 AS total_h,
                    EXTRACT(EPOCH FROM (min(a.occurred_at) FILTER (WHERE a.to_state = 'RECEIVED')) - r.created_at) / 3600.0 AS req_rcv,
                    EXTRACT(EPOCH FROM (min(a.occurred_at) FILTER (WHERE a.to_state = 'INSPECTED'))
                                     - (min(a.occurred_at) FILTER (WHERE a.to_state = 'RECEIVED'))) / 3600.0 AS rcv_insp,
                    EXTRACT(EPOCH FROM (min(a.occurred_at) FILTER (WHERE a.to_state = 'REFUNDED'))
                                     - (min(a.occurred_at) FILTER (WHERE a.to_state = 'INSPECTED'))) / 3600.0 AS insp_ref
                  FROM returns r LEFT JOIN audit_log a ON a.entity_type = 'return' AND a.entity_id = r.id::text
                  WHERE r.status IN ('REFUNDED','REJECTED') AND r.closed_at >= ? AND r.closed_at < ?
                  GROUP BY r.id)
                SELECT round(avg(total_h)::numeric, 1)::float8 AS avg_hours,
                       round((percentile_cont(0.5) WITHIN GROUP (ORDER BY total_h))::numeric, 1)::float8 AS p50_hours,
                       round((percentile_cont(0.9) WITHIN GROUP (ORDER BY total_h))::numeric, 1)::float8 AS p90_hours,
                       round(avg(req_rcv)::numeric, 1)::float8 AS request_to_receive_hours,
                       round(avg(rcv_insp)::numeric, 1)::float8 AS receive_to_inspect_hours,
                       round(avg(insp_ref)::numeric, 1)::float8 AS inspect_to_refund_hours
                FROM c""", f, t);
        Map<String, Object> cycleOut = new LinkedHashMap<>();
        cycleOut.put("avg_hours", cycle.get("avg_hours"));
        cycleOut.put("p50_hours", cycle.get("p50_hours"));
        cycleOut.put("p90_hours", cycle.get("p90_hours"));
        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("request_to_receive_hours", cycle.get("request_to_receive_hours"));
        stages.put("receive_to_inspect_hours", cycle.get("receive_to_inspect_hours"));
        stages.put("inspect_to_refund_hours", cycle.get("inspect_to_refund_hours"));
        cycleOut.put("stages", stages);
        out.put("cycle_time", cycleOut);

        out.put("returns_by_status", jdbc.queryForList("""
                SELECT status, count(*)::bigint AS count FROM returns WHERE created_at >= ? AND created_at < ?
                GROUP BY status ORDER BY count(*) DESC""", f, t));

        // daily series in business timezone, gaps filled
        Map<String, long[]> daily = new LinkedHashMap<>();
        LocalDate d0 = from.atZone(zone).toLocalDate();
        LocalDate d1 = to.atZone(zone).toLocalDate();
        for (LocalDate d = d0; !d.isAfter(d1); d = d.plusDays(1)) {
            daily.put(d.toString(), new long[2]);
        }
        jdbc.query("""
                SELECT (created_at AT TIME ZONE ?)::date AS d, count(*) FROM returns
                WHERE created_at >= ? AND created_at < ? GROUP BY 1""",
                rs -> {
                    long[] v = daily.get(rs.getDate(1).toLocalDate().toString());
                    if (v != null) {
                        v[0] = rs.getLong(2);
                    }
                }, zone.getId(), f, t);
        jdbc.query("""
                SELECT (closed_at AT TIME ZONE ?)::date AS d, count(*) FROM returns
                WHERE status = 'REFUNDED' AND closed_at >= ? AND closed_at < ? GROUP BY 1""",
                rs -> {
                    long[] v = daily.get(rs.getDate(1).toLocalDate().toString());
                    if (v != null) {
                        v[1] = rs.getLong(2);
                    }
                }, zone.getId(), f, t);
        List<Map<String, Object>> series = new ArrayList<>();
        daily.forEach((day, v) -> series.add(Map.of("date", day, "requested", v[0], "refunded", v[1])));
        out.put("daily", series);

        Map<String, Object> k = jdbc.queryForMap("""
                SELECT
                  (SELECT count(*) FROM returns WHERE created_at >= ? AND created_at < ?)::bigint AS returns_count,
                  (SELECT COALESCE(sum(approved_refund_paise), 0) FROM returns
                     WHERE status = 'REFUNDED' AND closed_at >= ? AND closed_at < ?)::bigint AS refunded_paise,
                  (SELECT count(*) FROM returns WHERE status = 'REFUNDED' AND closed_at >= ? AND closed_at < ?)::bigint AS refund_count,
                  (SELECT count(*) FROM returns WHERE closed_at IS NULL)::bigint AS open_returns,
                  (SELECT count(*) FROM customers WHERE is_flagged)::bigint AS flagged_customers,
                  (SELECT round(100.0 * count(*) FILTER (WHERE status = 'SUCCEEDED')
                          / NULLIF(count(*) FILTER (WHERE status IN ('SUCCEEDED','FAILED','UNKNOWN')), 0), 1)
                     FROM refund_attempts WHERE created_at >= ? AND created_at < ?)::float8 AS refund_success_rate""",
                f, t, f, t, f, t, f, t);
        Map<String, Object> kpis = new LinkedHashMap<>(k);
        kpis.put("avg_cycle_hours", cycle.get("avg_hours"));
        kpis.put("return_rate_pct", sold == 0 ? 0.0 : Math.round(10000.0 * returned / sold) / 100.0);
        out.put("kpis", kpis);
        return out;
    }

    public List<Map<String, Object>> flaggedCustomers() {
        return jdbc.queryForList("""
                SELECT cu.id AS customer_id, cu.full_name AS name,
                       regexp_replace(cu.email, '^(.).*(@.*)$', '\\1***\\2') AS email_masked,
                       cu.flagged_at, cu.flag_reason,
                       count(r.id) FILTER (WHERE r.created_at >= ?)::bigint AS returns_30d,
                       count(r.id) FILTER (WHERE r.closed_at IS NULL)::bigint AS open_returns,
                       COALESCE(sum(r.approved_refund_paise) FILTER (WHERE r.status = 'REFUNDED' AND r.closed_at >= ?), 0)::bigint
                         AS refunded_paise_90d
                FROM customers cu LEFT JOIN returns r ON r.customer_id = cu.id
                WHERE cu.is_flagged GROUP BY cu.id ORDER BY cu.flagged_at DESC""",
                Timestamp.from(clock.instant().minus(Duration.ofDays(30))),
                Timestamp.from(clock.instant().minus(Duration.ofDays(90))));
    }

    /** Gauges for Prometheus (refreshed by GaugeRefresher). */
    public Map<String, Long> openByStatus() {
        Map<String, Long> m = new LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) FROM returns WHERE closed_at IS NULL GROUP BY status",
                rs -> {
                    m.put(rs.getString(1), rs.getLong(2));
                });
        return m;
    }

    public long[] unknownAttempts() {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status = 'UNKNOWN')::bigint AS n,
                       COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)::bigint AS age
                FROM refund_attempts WHERE status IN ('UNKNOWN','PENDING') AND method = 'ORIGINAL_PAYMENT'""");
        return new long[] {((Number) r.get("n")).longValue(), ((Number) r.get("age")).longValue()};
    }

    public long flaggedCount() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM customers WHERE is_flagged", Long.class);
        return n == null ? 0 : n;
    }
}
