package com.returnright.finance;

import com.returnright.audit.AuditService;
import com.returnright.core.Actor;
import com.returnright.core.ApiException;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.refunds.RefundService;
import com.returnright.returns.ReturnRepository;
import com.returnright.returns.ReturnRequest;
import com.returnright.returns.ReturnStateMachine;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Finance review: approve (T13), reject (T14), retry (T17), recheck UNKNOWN (ADR-001 L6). */
@Service
public class FinanceService {

    private final ReturnStateMachine sm;
    private final ReturnRepository returns;
    private final RefundService refunds;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public FinanceService(ReturnStateMachine sm, ReturnRepository returns, RefundService refunds, AuditService audit,
                          JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.sm = sm;
        this.returns = returns;
        this.refunds = refunds;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    public void approve(Actor actor, UUID returnId, int version, String note) {
        Optional<UUID> attempt = tx.execute(s -> {
            ReturnRequest r = lockAndCheck(returnId, version, ReturnStatus.PENDING_FINANCE, ReturnEvent.FINANCE_APPROVE);
            insertReview(r, actor, "APPROVED", note); // must exist before REFUND_PENDING [BR-4][BR-10]
            ReturnRequest updated = sm.apply(returnId, ReturnEvent.FINANCE_APPROVE, actor, version, null);
            return refunds.initiate(updated, actor);
        });
        attempt.ifPresent(refunds::execute); // after commit (ADR-001)
    }

    public void reject(Actor actor, UUID returnId, int version, String note) {
        tx.executeWithoutResult(s -> {
            ReturnRequest r = lockAndCheck(returnId, version, ReturnStatus.PENDING_FINANCE, ReturnEvent.FINANCE_REJECT);
            insertReview(r, actor, "REJECTED", note);
            sm.apply(returnId, ReturnEvent.FINANCE_REJECT, actor, version, null);
        });
    }

    public void retry(Actor actor, UUID returnId, int version, RefundMethod switchTo) {
        Optional<UUID> attempt = tx.execute(s -> refunds.retry(returnId, actor, version, switchTo));
        attempt.ifPresent(refunds::execute);
    }

    public UUID recheck(Actor actor, UUID attemptId) {
        // The reviewer only asks for a gateway lookup; the gateway's answer decides the outcome. T15/T16
        // (REFUND_SUCCEEDED / REFUND_FAILED) are SYSTEM transitions, so apply them as SYSTEM - exactly like the
        // reconciler - and keep the human who asked in the audit trail. Passing the reviewer here made the state
        // machine reject the outcome with 403 FORBIDDEN_ROLE, so "Recheck" could never resolve an UNKNOWN attempt.
        refunds.recheck(attemptId, Actor.system("recheck"));
        UUID returnId = refunds.attemptReturnId(attemptId);
        tx.executeWithoutResult(s -> audit.record(actor, "refund_attempt", attemptId.toString(), "RECHECK_REQUESTED",
                null, null, null, null, Map.of("return_id", returnId.toString())));
        return returnId;
    }

    /** Lock first, then validate, so two reviewers serialise and the loser gets a clean 409 (F-04). */
    private ReturnRequest lockAndCheck(UUID id, int version, ReturnStatus expected, ReturnEvent ev) {
        ReturnRequest r = returns.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Return"));
        if (r.getVersion() != version) {
            throw ApiException.conflict("STALE_VERSION",
                    "This return was updated by someone else. Please reload and try again.",
                    Map.of("current_version", r.getVersion(), "current_state", r.getStatus().name()));
        }
        if (r.getStatus() != expected) {
            throw ApiException.conflict("ILLEGAL_TRANSITION",
                    "This return is no longer awaiting finance review (it is %s)."
                            .formatted(r.getStatus().name().toLowerCase().replace('_', ' ')),
                    Map.of("current_state", r.getStatus().name(), "event", ev.name()));
        }
        return r;
    }

    private void insertReview(ReturnRequest r, Actor actor, String decision, String note) {
        jdbc.update("""
                INSERT INTO finance_reviews (id, return_id, reviewer_id, decision, amount_paise_reviewed, note, decided_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), r.getId(), actor.userId(), decision,
                r.getApprovedRefundPaise(), note == null || note.isBlank() ? null : note.strip(),
                Timestamp.from(clock.instant()));
        audit.record(actor, "finance_review", r.getId().toString(), "FINANCE_" + decision, null, decision, null,
                Map.of("amount_paise_reviewed", r.getApprovedRefundPaise()), Map.of("return_id", r.getId().toString()));
    }
}
