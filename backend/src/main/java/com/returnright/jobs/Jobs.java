package com.returnright.jobs;

import com.returnright.analytics.service.AnalyticsService;
import com.returnright.core.model.Actor;
import com.returnright.core.config.AppClock;
import com.returnright.core.web.Correlation;
import com.returnright.core.infra.IdempotencyService;
import com.returnright.core.infra.Metrics;
import com.returnright.core.config.RrProperties;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.ledger.service.LedgerService;
import com.returnright.refunds.service.RefundService;
import com.returnright.returns.service.ReturnStateMachine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background loops (Part 2 §17 jobs). Each run takes a Postgres advisory lock so only one instance executes it.
 * Disabled with rr.jobs.enabled=false (seeding, some tests).
 */
@Component
@ConditionalOnProperty(prefix = "rr.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Jobs {

    private static final Logger log = LoggerFactory.getLogger(Jobs.class);

    private final RefundService refunds;
    private final ReturnStateMachine sm;
    private final IdempotencyService idempotency;
    private final AnalyticsService analytics;
    private final LedgerService ledger;
    private final OutboxRelay outbox;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Metrics metrics;
    private final AppClock clock;
    private final RrProperties props;

    private final AtomicLong unknown = new AtomicLong();
    private final AtomicLong unknownAge = new AtomicLong();
    private final AtomicLong flagged = new AtomicLong();
    private final Map<String, AtomicLong> open = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> recon = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    public Jobs(RefundService refunds, ReturnStateMachine sm, IdempotencyService idempotency, AnalyticsService analytics,
                LedgerService ledger, OutboxRelay outbox, JdbcTemplate jdbc, TransactionTemplate tx, Metrics metrics,
                AppClock clock, RrProperties props) {
        this.refunds = refunds;
        this.sm = sm;
        this.idempotency = idempotency;
        this.analytics = analytics;
        this.ledger = ledger;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.tx = tx;
        this.metrics = metrics;
        this.clock = clock;
        this.props = props;
        this.registry = metrics.registry();
        Gauge.builder("rr.refund.attempts.unknown", unknown, AtomicLong::get).register(registry);
        Gauge.builder("rr.refund.attempts.unknown.oldest.age.seconds", unknownAge, AtomicLong::get).register(registry);
        Gauge.builder("rr.customers.flagged", flagged, AtomicLong::get).register(registry);
    }

    /** ADR-001 L6: resolve UNKNOWN and stale PENDING attempts by asking the gateway (same key). */
    @Scheduled(fixedDelayString = "${rr.jobs.reconciler-interval-ms:15000}", initialDelay = 10_000)
    public void reconcileRefunds() {
        withLock(7_001, "reconciler", () -> {
            Instant stale = clock.instant().minus(Duration.ofSeconds(props.jobs().stalePendingSeconds()));
            List<UUID> ids = refunds.reconcileCandidates(stale);
            for (UUID id : ids.stream().limit(25).toList()) {
                try {
                    refunds.recheck(id, Actor.system("reconciler"));
                } catch (RuntimeException e) {
                    log.warn("reconcile {} failed: {}", id, e.toString());
                }
            }
            metrics.reconcilerRun("ok");
        });
    }

    /** T6: cancel APPROVED returns whose label expired [A-19]. */
    @Scheduled(fixedDelayString = "${rr.jobs.label-expiry-interval-ms:300000}", initialDelay = 30_000)
    public void expireLabels() {
        withLock(7_002, "label-expiry", () -> {
            List<UUID> ids = jdbc.queryForList("""
                    SELECT r.id FROM returns r JOIN return_labels l ON l.return_id = r.id AND l.status = 'ACTIVE'
                    WHERE r.status = 'APPROVED' AND l.expires_at < ? LIMIT 200""", UUID.class,
                    java.sql.Timestamp.from(clock.instant()));
            for (UUID id : ids) {
                try {
                    tx.executeWithoutResult(s -> sm.apply(id, ReturnEvent.LABEL_EXPIRE, Actor.system("label-expiry"),
                            null, null));
                } catch (RuntimeException e) {
                    log.warn("label expiry {} failed: {}", id, e.toString());
                }
            }
        });
    }

    @Scheduled(fixedDelayString = "${rr.jobs.outbox-interval-ms:2000}", initialDelay = 5_000)
    public void relayOutbox() {
        if (outbox.enabled()) {
            withLock(7_003, "outbox", outbox::relayBatch);
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void purgeIdempotencyKeys() {
        withLock(7_004, "idempotency-cleanup", () -> log.info("purged {} idempotency keys", idempotency.purgeExpired()));
    }

    /** Gauges for Prometheus (Part 2 §15.1). */
    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void refreshGauges() {
        try {
            long[] u = analytics.unknownAttempts();
            unknown.set(u[0]);
            unknownAge.set(u[1]);
            flagged.set(analytics.flaggedCount());
            Map<String, Long> byStatus = analytics.openByStatus();
            open.keySet().forEach(k -> open.get(k).set(0));
            byStatus.forEach((status, n) -> open.computeIfAbsent(status, s -> {
                AtomicLong v = new AtomicLong();
                Gauge.builder("rr.returns.open", v, AtomicLong::get).tag("status", s).register(registry);
                return v;
            }).set(n));
            for (LedgerService.Check c : ledger.reconcile(15)) {
                recon.computeIfAbsent(c.check(), k -> {
                    AtomicLong v = new AtomicLong();
                    Gauge.builder("rr.reconciliation.mismatches", v, AtomicLong::get).tag("check", k).register(registry);
                    return v;
                }).set(c.mismatches());
            }
        } catch (RuntimeException e) {
            log.debug("gauge refresh failed: {}", e.toString());
        }
    }

    private void withLock(long key, String job, Runnable body) {
        Correlation.start(null);
        try {
            jdbc.execute((ConnectionCallback<Void>) con -> {
                boolean locked;
                try (var st = con.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                    st.setLong(1, key);
                    try (var rs = st.executeQuery()) {
                        locked = rs.next() && rs.getBoolean(1);
                    }
                }
                if (!locked) {
                    return null;
                }
                try {
                    body.run();
                } catch (RuntimeException e) {
                    metrics.reconcilerRun("error");
                    log.warn("job {} failed: {}", job, e.toString());
                } finally {
                    try (var st = con.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                        st.setLong(1, key);
                        st.execute();
                    }
                }
                return null;
            });
        } finally {
            Correlation.clear();
        }
    }
}
