package com.returnright.inspection.service;

import com.returnright.labels.service.LabelService;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import com.returnright.core.enums.Decision;
import com.returnright.core.enums.Grade;
import com.returnright.core.enums.ItemStatus;
import com.returnright.core.enums.RejectReason;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.labels.repository.ReturnLabelRepository;
import com.returnright.labels.entity.ReturnLabel;
import com.returnright.refunds.service.RefundService;
import com.returnright.returns.entity.ReturnItem;
import com.returnright.returns.repository.ReturnRepository;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.service.ReturnStateMachine;
import com.returnright.returns.service.ReturnStateMachine.InspectPayload;
import com.returnright.returns.service.ReturnStateMachine.ScanPayload;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Warehouse flows: scan (T4, idempotent — F-05/F-06), inspect (T7/T8), complete + route (T9-T12). */
@Service
public class InspectionService {

    public record ScanResult(boolean alreadyReceived, String message, UUID returnId) {}

    private static final EnumSet<ReturnStatus> AT_OR_PAST_RECEIVED = EnumSet.of(ReturnStatus.RECEIVED,
            ReturnStatus.INSPECTED, ReturnStatus.PENDING_FINANCE, ReturnStatus.REFUND_PENDING,
            ReturnStatus.REFUND_FAILED, ReturnStatus.REFUNDED, ReturnStatus.REJECTED);

    private final ReturnStateMachine sm;
    private final ReturnRepository returns;
    private final LabelService labels;
    private final RefundService refunds;
    private final TransactionTemplate tx;

    public InspectionService(ReturnStateMachine sm, ReturnRepository returns, LabelService labels,
                             RefundService refunds, TransactionTemplate tx) {
        this.sm = sm;
        this.returns = returns;
        this.labels = labels;
        this.refunds = refunds;
        this.tx = tx;
    }

    public ScanResult scan(Actor actor, String tokenOrRma) {
        String input = tokenOrRma == null ? "" : tokenOrRma.strip();
        return tx.execute(s -> {
            ReturnLabel label = labels.byToken(input).orElse(null);
            ReturnRequest r;
            if (label != null) {
                r = returns.findForUpdate(label.getReturnId()).orElseThrow();
            } else {
                r = returns.findByRmaNumber(input.toUpperCase()).flatMap(x -> returns.findForUpdate(x.getId()))
                        .orElseThrow(() -> new ApiException("NOT_FOUND",
                                "No return matches this label. Check the code or enter the RMA number.",
                                org.springframework.http.HttpStatus.NOT_FOUND));
                label = labels.current(r.getId()).orElseThrow(() -> ApiException.notFound("Return label"));
            }
            if (r.getStatus() == ReturnStatus.CANCELLED) { // F-05
                throw ApiException.conflict("RETURN_CANCELLED",
                        "Do not accept this parcel: return %s was cancelled. Send it back to the customer."
                                .formatted(r.getRmaNumber()), Map.of("rma_number", r.getRmaNumber()));
            }
            if (AT_OR_PAST_RECEIVED.contains(r.getStatus())) { // F-06 double scan: harmless no-op
                return new ScanResult(true, "Already received — no action needed.", r.getId());
            }
            sm.apply(r.getId(), ReturnEvent.SCAN_RECEIVE, actor, null, new ScanPayload(label));
            return new ScanResult(false, "Parcel received. Ready for inspection.", r.getId());
        });
    }

    public void inspectItem(Actor actor, UUID returnId, UUID itemId, int version, Decision decision, Grade grade,
                            RejectReason rejectReason, String notes) {
        tx.executeWithoutResult(s -> {
            ReturnRequest r = returns.findById(returnId).orElseThrow(() -> ApiException.notFound("Return"));
            ReturnItem item = r.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                    .orElseThrow(() -> ApiException.notFound("Return item"));
            ReturnEvent ev = item.getStatus() == ItemStatus.RECEIVED ? ReturnEvent.INSPECT_ITEM : ReturnEvent.REGRADE_ITEM;
            sm.apply(returnId, ev, actor, version, new InspectPayload(itemId, decision, grade, rejectReason,
                    notes == null || notes.isBlank() ? null : notes.strip()));
        });
    }

    /** T9 then ROUTE (T10/T11/T12) in one txn; an auto-refund executes after commit. */
    public void complete(Actor actor, UUID returnId, int version) {
        Optional<UUID> attempt = tx.execute(s -> {
            sm.apply(returnId, ReturnEvent.COMPLETE_INSPECTION, actor, version, null);
            ReturnRequest routed = sm.apply(returnId, ReturnEvent.ROUTE, Actor.system("router"), null, null);
            if (routed.getStatus() == ReturnStatus.REFUND_PENDING) {
                return refunds.initiate(routed, Actor.system("router"));
            }
            return Optional.<UUID>empty();
        });
        attempt.ifPresent(refunds::execute);
    }
}
