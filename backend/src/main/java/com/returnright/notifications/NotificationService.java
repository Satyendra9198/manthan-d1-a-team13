package com.returnright.notifications;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** In-app customer notifications [A-26], written in the caller's transaction. */
@Service
public class NotificationService {

    public record Notification(UUID id, String template, String title, String body, UUID returnId, Instant createdAt,
                               Instant readAt) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(long customerId, UUID returnId, String template, String title, String body) {
        jdbc.update("""
                INSERT INTO notifications (id, customer_id, return_id, template, title, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), customerId, returnId, template, title, body, Timestamp.from(clock.instant()));
    }

    public List<Notification> forCustomer(long customerId, int limit) {
        return jdbc.query("""
                SELECT id, template, title, body, return_id, created_at, read_at FROM notifications
                WHERE customer_id = ? ORDER BY created_at DESC LIMIT ?""",
                (rs, i) -> new Notification(rs.getObject("id", UUID.class), rs.getString("template"),
                        rs.getString("title"), rs.getString("body"), rs.getObject("return_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                customerId, limit);
    }

    @Transactional
    public void markAllRead(long customerId) {
        jdbc.update("UPDATE notifications SET read_at = ? WHERE customer_id = ? AND read_at IS NULL",
                Timestamp.from(clock.instant()), customerId);
    }
}
