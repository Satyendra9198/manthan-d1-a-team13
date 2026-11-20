package com.returnright.audit;

import com.returnright.core.Actor;
import com.returnright.core.Correlation;
import com.returnright.core.Json;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit log (Part 2 §13). {@link #record} REQUIRES the caller's transaction, so the audit row and the
 * state change commit or roll back together. Rows are never updated or deleted (trigger + REVOKE) [BR-20].
 */
@Service
public class AuditService {

    public record Entry(long id, Instant occurredAt, String correlationId, String actorType, String actorId,
                        String actorRole, String entityType, String entityId, String event, String fromState,
                        String toState) {}

    private final JdbcTemplate jdbc;
    private final Json json;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, Json json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, String entityType, String entityId, String event, String fromState,
                       String toState, Map<String, Object> before, Map<String, Object> after,
                       Map<String, Object> meta) {
        jdbc.update("""
                INSERT INTO audit_log (occurred_at, correlation_id, actor_type, actor_id, actor_role, entity_type,
                  entity_id, event, from_state, to_state, before_data, after_data, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)""",
                Timestamp.from(clock.instant()), Correlation.id(), actor.type().name(), actor.id(), actor.role().name(),
                entityType, entityId, event, fromState, toState,
                before == null ? null : json.write(before), after == null ? null : json.write(after),
                json.write(meta == null ? Map.of() : meta));
    }

    /** First time a return entered each state: drives the customer timeline and cycle-time analytics. */
    public Map<String, Instant> firstEntered(String entityType, String entityId) {
        Map<String, Instant> out = new java.util.LinkedHashMap<>();
        jdbc.query("""
                SELECT to_state, min(occurred_at) AS at FROM audit_log
                WHERE entity_type = ? AND entity_id = ? AND to_state IS NOT NULL GROUP BY to_state""",
                rs -> {
                    out.put(rs.getString("to_state"), rs.getTimestamp("at").toInstant());
                }, entityType, entityId);
        return out;
    }

    public List<Entry> search(String entityId, String correlationId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, occurred_at, correlation_id, actor_type, actor_id, actor_role, entity_type, entity_id,
                       event, from_state, to_state FROM audit_log WHERE 1=1""");
        List<Object> args = new java.util.ArrayList<>();
        if (entityId != null && !entityId.isBlank()) {
            sql.append(" AND (entity_id = ? OR metadata->>'return_id' = ?)");
            args.add(entityId.trim());
            args.add(entityId.trim());
        }
        if (correlationId != null && !correlationId.isBlank()) {
            sql.append(" AND correlation_id = ?");
            args.add(correlationId.trim());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        return jdbc.query(sql.toString(), (rs, i) -> new Entry(rs.getLong("id"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("correlation_id"), rs.getString("actor_type"),
                rs.getString("actor_id"), rs.getString("actor_role"), rs.getString("entity_type"),
                rs.getString("entity_id"), rs.getString("event"), rs.getString("from_state"),
                rs.getString("to_state")), args.toArray());
    }
}
