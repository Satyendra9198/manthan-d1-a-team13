package com.returnright.refunds.service;

import com.returnright.refunds.entity.Refund;
import com.returnright.refunds.entity.RefundAttempt;
import com.returnright.audit.service.AuditService;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import com.returnright.core.infra.Metrics;
import com.returnright.core.enums.AttemptStatus;
import com.returnright.core.enums.Decision;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.RefundStatus;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.gatewayclient.GatewayClient;
import com.returnright.gatewayclient.GatewayClient.Outcome;
import com.returnright.ledger.service.LedgerService;
import com.returnright.ledger.service.LedgerService.RefundPosting;
import com.returnright.ledger.service.LedgerService.UnitLine;
import com.returnright.refunds.repository.RefundAttemptRepository;
import com.returnright.refunds.repository.RefundRepository;
import com.returnright.returns.entity.ReturnItem;
import com.returnright.returns.repository.ReturnRepository;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.service.ReturnStateMachine;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refund execution per ADR-001:
 * <ol>
 *   <li>{@link #initiate} creates refund + items + attempt (PENDING, key persisted) in the caller's txn.</li>
 *   <li>{@link #execute} runs AFTER COMMIT, outside any transaction, and calls the gateway with that key.</li>
 *   <li>{@link #finalizeAttempt} locks the attempt and is a no-op if it is already terminal.</li>
 *   <li>Ambiguity -> UNKNOWN; only {@link #recheck} (reconciler / finance "Recheck") resolves it, reusing the key.</li>
 * </ol>
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refunds;
    private final RefundAttemptRepository attempts;
    private final ReturnRepository returns;
    private final ReturnStateMachine sm;
    private final LedgerService ledger;
    private final GatewayClient gateway;
    private final AuditService audit;
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final Metrics metrics;
    private final Clock clock;

    public RefundService(RefundRepository refunds, RefundAttemptRepository attempts, ReturnRepository returns,
                         ReturnStateMachine sm, LedgerService ledger, GatewayClient gateway, AuditService audit,
                         EntityManager em, TransactionTemplate tx, Metrics metrics, Clock clock) {
        this.refunds = refunds;
        this.attempts = attempts;
        this.returns = returns;
        this.sm = sm;
        this.ledger = ledger;
        this.gateway = gateway;
        this.audit = audit;
        this.em = em;
        this.tx = tx;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Called in the same txn that moved the return to REFUND_PENDING (T12/T13). Store credit completes inline;
     * original-payment refunds return the attempt id to execute after commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> initiate(ReturnRequest r, Actor actor) {
        if (r.getStatus() != ReturnStatus.REFUND_PENDING) {
            throw new IllegalStateException("initiate requires REFUND_PENDING, was " + r.getStatus());
        }
        Instant now = clock.instant();
        Refund refund = new Refund(r.getId(), r.getCustomer().getId(), r.getApprovedRefundPaise(), now);
        em.persist(refund);
        em.flush(); // refunds.return_id UNIQUE [BR-11] fails fast on a duplicate
        for (ReturnItem i : r.getItems()) {
            if (i.getDecision() == Decision.ACCEPT && i.getRefundPaise() != null && i.getRefundPaise() > 0) {
                em.createNativeQuery("INSERT INTO refund_items (refund_id, return_item_id, amount_paise) VALUES (?, ?, ?)")
                        .setParameter(1, refund.getId()).setParameter(2, i.getId()).setParameter(3, i.getRefundPaise())
                        .executeUpdate();
            }
        }
        audit.record(actor, "refund", refund.getId().toString(), "REFUND_CREATED", null, RefundStatus.PENDING.name(),
                null, Map.of("amount_paise", refund.getAmountPaise()), Map.of("return_id", r.getId().toString()));
        return newAttempt(refund, 1, r.getRefundMethod(), r, actor);
    }

    private Optional<UUID> newAttempt(Refund refund, int no, RefundMethod method, ReturnRequest r, Actor actor) {
        RefundAttempt a = new RefundAttempt(refund.getId(), no, method, clock.instant());
        em.persist(a);
        em.flush(); // uq_refund_one_live_attempt [BR-15]
        audit.record(actor, "refund_attempt", a.getId().toString(), "ATTEMPT_CREATED", null, "PENDING", null,
                Map.of("attempt_no", no, "method", method.name(), "gateway_key", a.getGatewayIdempotencyKey()),
                Map.of("return_id", r.getId().toString()));
        if (method == RefundMethod.STORE_CREDIT) { // [A-16] ledger only, completes in this same txn (F-20)
            succeed(a, refund, r, null, Actor.system("store-credit"));
            return Optional.empty();
        }
        return Optional.of(a.getId());
    }

    /** Post-commit gateway call. Never called inside a transaction. */
    public void execute(UUID attemptId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("gateway must not be called inside a transaction");
        }
        record Call(String key, String paymentRef, long amount, String merchantRef) {}
        Call call = tx.execute(s -> {
            RefundAttempt a = attempts.findById(attemptId).orElseThrow();
            if (a.isTerminal()) {
                return null;
            }
            Refund refund = refunds.findById(a.getRefundId()).orElseThrow();
            ReturnRequest r = returns.findById(refund.getReturnId()).orElseThrow();
            a.markSent(clock.instant());
            return new Call(a.getGatewayIdempotencyKey(), r.getOrder().getPaymentReference(), refund.getAmountPaise(),
                    "refund:" + refund.getId());
        });
        if (call == null) {
            return;
        }
        Outcome o = gateway.createRefund(call.key(), call.paymentRef(), call.amount(), call.merchantRef());
        finalizeAttempt(attemptId, o, Actor.system("refunds"));
    }

    /** Idempotent: locks the attempt; terminal attempts are left untouched (F-09). */
    public AttemptStatus finalizeAttempt(UUID attemptId, Outcome o, Actor actor) {
        return tx.execute(s -> {
            RefundAttempt a = attempts.findForUpdate(attemptId).orElseThrow();
            if (a.isTerminal()) {
                return a.getStatus();
            }
            Refund refund = refunds.findById(a.getRefundId()).orElseThrow();
            ReturnRequest r = returns.findById(refund.getReturnId()).orElseThrow();
            Instant now = clock.instant();
            a.markChecked(now);
            switch (o.status()) {
                case SUCCEEDED -> succeed(a, refund, r, o.gatewayRefundId(), actor);
                case FAILED -> {
                    a.fail(o.declineCode(), now);
                    refund.setStatus(RefundStatus.FAILED, now);
                    audit.record(actor, "refund_attempt", a.getId().toString(), "GATEWAY_DECLINED", "PENDING", "FAILED",
                            null, null, Map.of("decline_code", o.declineCode(), "return_id", r.getId().toString()));
                    sm.apply(r.getId(), ReturnEvent.REFUND_FAILED, actor, null, null);
                    afterCommit(() -> metrics.refundAttempt(a.getMethod().name(), "failed"));
                }
                case PROCESSING, UNKNOWN, NOT_FOUND -> {
                    if (a.getStatus() != AttemptStatus.UNKNOWN) {
                        a.unknown();
                        audit.record(actor, "refund_attempt", a.getId().toString(), "OUTCOME_UNKNOWN", "PENDING",
                                "UNKNOWN", null, null, Map.of("detail", String.valueOf(o.detail()),
                                        "return_id", r.getId().toString()));
                        afterCommit(() -> metrics.refundAttempt(a.getMethod().name(), "unknown"));
                    }
                }
            }
            return a.getStatus();
        });
    }

    private void succeed(RefundAttempt a, Refund refund, ReturnRequest r, String gatewayRefundId, Actor actor) {
        Instant now = clock.instant();
        String from = a.getStatus().name();
        a.succeed(gatewayRefundId, now);
        refund.setStatus(RefundStatus.SUCCEEDED, now);
        List<UnitLine> units = r.getItems().stream()
                .filter(i -> i.getDecision() == Decision.ACCEPT && i.getRefundPaise() != null && i.getRefundPaise() > 0)
                .map(i -> new UnitLine(i.getId(), i.getUnitPricePaise(), i.getRefundPaise())).toList();
        em.flush();
        long txn = ledger.postRefund(new RefundPosting(r.getId(), a.getId(), r.getCustomer().getId(),
                a.getMethod() == RefundMethod.STORE_CREDIT, units, actor.id(),
                "Refund %s attempt %d".formatted(r.getRmaNumber(), a.getAttemptNo())));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("return_id", r.getId().toString());
        meta.put("ledger_transaction_id", txn);
        meta.put("gateway_refund_id", gatewayRefundId);
        audit.record(actor, "refund_attempt", a.getId().toString(), "REFUND_SUCCEEDED", from, "SUCCEEDED", null,
                Map.of("amount_paise", refund.getAmountPaise()), meta);
        sm.apply(r.getId(), ReturnEvent.REFUND_SUCCEEDED, actor, null, null);
        String method = a.getMethod().name();
        long amount = refund.getAmountPaise();
        afterCommit(() -> {
            metrics.refundAttempt(method, "succeeded");
            metrics.refundAmount(method, amount);
        });
    }

    /** Reconciler / finance "Recheck": ask the gateway by key; if it never saw it, re-POST the SAME key (F-02/F-07). */
    public AttemptStatus recheck(UUID attemptId, Actor actor) {
        record Info(String key, String paymentRef, long amount, String merchantRef, boolean terminal) {}
        Info info = tx.execute(s -> {
            RefundAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Refund attempt"));
            Refund refund = refunds.findById(a.getRefundId()).orElseThrow();
            ReturnRequest r = returns.findById(refund.getReturnId()).orElseThrow();
            return new Info(a.getGatewayIdempotencyKey(), r.getOrder().getPaymentReference(), refund.getAmountPaise(),
                    "refund:" + refund.getId(), a.isTerminal() || a.getMethod() == RefundMethod.STORE_CREDIT);
        });
        if (info.terminal()) {
            return attempts.findById(attemptId).orElseThrow().getStatus();
        }
        Outcome o = gateway.getRefund(info.key());
        if (o.status() == GatewayClient.Status.NOT_FOUND) {
            log.info("gateway has no record of {}, re-sending with the same key", info.key());
            o = gateway.createRefund(info.key(), info.paymentRef(), info.amount(), info.merchantRef());
        }
        return finalizeAttempt(attemptId, o, actor);
    }

    /** T17: only after a DEFINITIVE failure; never while an attempt is UNKNOWN (DB-enforced too, [BR-15]). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> retry(UUID returnId, Actor actor, int version, RefundMethod switchTo) {
        Refund refund = refunds.findByReturnId(returnId).orElseThrow(() -> ApiException.notFound("Refund"));
        List<RefundAttempt> all = attempts.findByRefund(refund.getId());
        if (all.stream().anyMatch(a -> !a.isTerminal())) {
            throw ApiException.conflict("REFUND_ATTEMPT_IN_FLIGHT",
                    "A refund attempt is still unresolved. Use 'Recheck with gateway' instead of retrying.", Map.of());
        }
        ReturnRequest r = sm.apply(returnId, ReturnEvent.RETRY_REFUND, actor, version, switchTo);
        refund.setStatus(RefundStatus.PENDING, null);
        int next = all.stream().mapToInt(RefundAttempt::getAttemptNo).max().orElse(0) + 1;
        return newAttempt(refund, next, r.getRefundMethod(), r, actor);
    }

    // ---------------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------------
    public record RefundView(Refund refund, List<RefundAttempt> attempts) {}

    @Transactional(readOnly = true)
    public Map<UUID, RefundView> viewsFor(List<UUID> returnIds) {
        Map<UUID, RefundView> out = new LinkedHashMap<>();
        if (returnIds.isEmpty()) {
            return out;
        }
        List<Refund> rs = refunds.findByReturnIds(returnIds);
        Map<UUID, List<RefundAttempt>> byRefund = new LinkedHashMap<>();
        if (!rs.isEmpty()) {
            for (RefundAttempt a : attempts.findByRefunds(rs.stream().map(Refund::getId).toList())) {
                byRefund.computeIfAbsent(a.getRefundId(), k -> new java.util.ArrayList<>()).add(a);
            }
        }
        for (Refund f : rs) {
            out.put(f.getReturnId(), new RefundView(f, byRefund.getOrDefault(f.getId(), List.of())));
        }
        return out;
    }

    public List<UUID> reconcileCandidates(Instant stalePendingBefore) {
        return attempts.findReconcileCandidates(stalePendingBefore);
    }

    public UUID attemptReturnId(UUID attemptId) {
        RefundAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Refund attempt"));
        return refunds.findById(a.getRefundId()).orElseThrow().getReturnId();
    }

    static ApiException inFlight() {
        return new ApiException("REFUND_ATTEMPT_IN_FLIGHT", "A refund attempt is still unresolved.", HttpStatus.CONFLICT);
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        }
    }
}
