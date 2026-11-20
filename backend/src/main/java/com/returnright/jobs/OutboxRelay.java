package com.returnright.jobs;

import com.returnright.core.config.RrProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox_events to Kafka (ADR-002). At-least-once: rows are marked published only after the broker acks;
 * consumers dedupe on {@code event_id} (the outbox id, inside the JSON). The Kafka message key is the return id, so
 * events for one return stay ordered on one partition.
 */
@Component
public class OutboxRelay implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final RrProperties props;
    private final Clock clock;
    private final String bootstrap;
    private KafkaProducer<String, String> producer;

    public OutboxRelay(JdbcTemplate jdbc, RrProperties props, Clock clock,
                       @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrap) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
        this.bootstrap = bootstrap;
    }

    public boolean enabled() {
        return props.kafka() != null && props.kafka().enabled();
    }

    public void relayBatch() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload, correlation_id, created_at
                FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT 200""");
        if (rows.isEmpty()) {
            return;
        }
        KafkaProducer<String, String> p = producer();
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String value = """
                    {"event_id":%d,"event_type":"%s","aggregate_type":"%s","aggregate_id":"%s","correlation_id":"%s","occurred_at":"%s","payload":%s}"""
                    .formatted(id, row.get("event_type"), row.get("aggregate_type"), row.get("aggregate_id"),
                            row.get("correlation_id"), ((Timestamp) row.get("created_at")).toInstant(), row.get("payload"));
            try {
                p.send(new ProducerRecord<>(props.kafka().topic(), String.valueOf(row.get("aggregate_id")), value))
                        .get(5, TimeUnit.SECONDS);
                jdbc.update("UPDATE outbox_events SET published_at = ? WHERE id = ?", Timestamp.from(clock.instant()), id);
            } catch (Exception e) {
                log.warn("outbox relay stopped at event {}: {}", id, e.toString());
                return; // keep order; retry next tick
            }
        }
    }

    private synchronized KafkaProducer<String, String> producer() {
        if (producer == null) {
            Properties p = new Properties();
            p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            p.put(ProducerConfig.ACKS_CONFIG, "all");
            p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            p.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
            producer = new KafkaProducer<>(p);
        }
        return producer;
    }

    @Override
    public synchronized void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
