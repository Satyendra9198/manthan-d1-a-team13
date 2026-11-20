package com.returnright.core.infra;

import com.returnright.core.util.Json;
import com.returnright.core.web.Correlation;
import java.time.Clock;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox (ADR-002): domain events are written in the SAME transaction as the state change and
 * relayed to Kafka later by OutboxRelayJob. If Kafka is off, events simply wait.
 */
@Service
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final Json json;
    private final Clock clock;

    public OutboxService(JdbcTemplate jdbc, Json json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, correlation_id, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?)""",
                aggregateType, aggregateId, eventType, json.write(payload), Correlation.id(),
                java.sql.Timestamp.from(clock.instant()));
    }
}
