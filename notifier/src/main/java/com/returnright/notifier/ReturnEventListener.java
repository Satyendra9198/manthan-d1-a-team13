package com.returnright.notifier;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivery is at-least-once (outbox + Kafka), so events are deduplicated on {@code event_id}. An event is marked as
 * seen only after the mail was handed to SMTP; if sending throws, the listener's error handler redelivers it.
 * The dedupe window is in memory: a restart between "sent" and "offset committed" can repeat one email, which is
 * acceptable for notifications (never for money - that path does not go through Kafka).
 */
@Component
public class ReturnEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReturnEventListener.class);
    private static final int DEDUPE_WINDOW = 50_000;

    private final JavaMailSender mail;
    private final MeterRegistry metrics;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String from;
    private final Set<String> seen = Collections.newSetFromMap(new LinkedHashMap<>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > DEDUPE_WINDOW;
        }
    });

    public ReturnEventListener(JavaMailSender mail, MeterRegistry metrics,
                               @Value("${notifier.from:ReturnRight <no-reply@returnright.dev>}") String from) {
        this.mail = mail;
        this.metrics = metrics;
        this.from = from;
    }

    @KafkaListener(topics = "${notifier.topic:returnright.events}", groupId = "${notifier.group:notifier}")
    @SuppressWarnings("unchecked")
    public void onEvent(String message) {
        Map<String, Object> envelope = json.readValue(message, Map.class);
        String eventId = String.valueOf(envelope.get("event_id"));
        synchronized (seen) {
            if (seen.contains(eventId)) {
                metrics.counter("notifier.events", "result", "duplicate").increment();
                return;
            }
        }
        var email = EmailComposer.compose(envelope);
        if (email.isEmpty()) {
            metrics.counter("notifier.events", "result", "skipped").increment();
        } else {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(email.get().to());
            m.setSubject(email.get().subject());
            m.setText(email.get().body());
            mail.send(m);   // throws -> redelivered by the Kafka error handler, not marked seen
            metrics.counter("notifier.events", "result", "sent").increment();
            log.info("event {} -> emailed {} ({})", eventId, email.get().to(), email.get().subject());
        }
        synchronized (seen) {
            seen.add(eventId);
        }
    }
}
