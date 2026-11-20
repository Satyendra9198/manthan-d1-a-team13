package com.returnright.api.mapper;

import com.returnright.labels.service.LabelService;
import com.returnright.api.dto.response.ListResponse;
import com.returnright.audit.service.AuditService;
import com.returnright.core.model.Actor;
import com.returnright.core.util.Money;
import com.returnright.core.enums.AttemptStatus;
import com.returnright.core.enums.ItemStatus;
import com.returnright.core.enums.LabelStatus;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.core.enums.Role;
import com.returnright.labels.repository.ReturnLabelRepository;
import com.returnright.labels.entity.ReturnLabel;
import com.returnright.refunds.entity.RefundAttempt;
import com.returnright.refunds.service.RefundService;
import com.returnright.refunds.service.RefundService.RefundView;
import com.returnright.returns.entity.ReturnItem;
import com.returnright.returns.repository.ReturnRepository;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.service.ReturnService;
import com.returnright.api.dto.response.AttemptDto;
import com.returnright.api.dto.response.CustomerSummary;
import com.returnright.api.dto.response.ItemDto;
import com.returnright.api.dto.response.LabelSummary;
import com.returnright.api.dto.response.RefundDto;
import com.returnright.api.dto.response.ReturnDto;
import com.returnright.api.dto.response.TimelineEntry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds ReturnDto: customer-safe status text, timeline (Part 1 §4.4) and allowed_actions for the caller. */
@Component
public class ReturnViewAssembler {

    private static final List<String> STEPS = List.of("REQUESTED", "APPROVED", "IN_TRANSIT", "RECEIVED", "INSPECTED");
    private static final Map<String, String> STEP_LABELS = Map.of("REQUESTED", "Requested", "APPROVED", "Approved",
            "IN_TRANSIT", "In transit", "RECEIVED", "Received", "INSPECTED", "Inspected", "REFUNDED", "Refunded",
            "REJECTED", "Rejected", "CANCELLED", "Cancelled");

    private final LabelService labels;
    private final RefundService refunds;
    private final AuditService audit;
    private final ReturnService returnService;
    private final ReturnRepository returnRepo;
    private final Clock clock;

    public ReturnViewAssembler(LabelService labels, RefundService refunds, AuditService audit,
                               ReturnService returnService, ReturnRepository returnRepo, Clock clock) {
        this.labels = labels;
        this.refunds = refunds;
        this.audit = audit;
        this.returnService = returnService;
        this.returnRepo = returnRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReturnDto one(UUID id, Actor viewer) {
        ReturnRequest r = viewer.role() == Role.CUSTOMER ? returnService.getForCustomer(id, viewer.customerId())
                : returnService.get(id);
        return many(List.of(r), viewer).getFirst();
    }

    @Transactional(readOnly = true)
    public ListResponse<ReturnDto> byStatus(List<ReturnStatus> statuses, int limit, Actor viewer) {
        int lim = Math.min(Math.max(limit, 1), 200);
        List<ReturnDto> dtos = many(returnRepo.findByStatusIn(statuses, PageRequest.of(0, lim)), viewer);
        return new ListResponse<>(dtos, returnRepo.countByStatusIn(statuses));
    }

    @Transactional(readOnly = true)
    public ListResponse<ReturnDto> forCustomer(long customerId, Actor viewer) {
        List<ReturnDto> dtos = many(returnRepo.findByCustomer(customerId), viewer);
        return new ListResponse<>(dtos, dtos.size());
    }

    @Transactional(readOnly = true)
    public List<ReturnDto> many(List<ReturnRequest> rs, Actor viewer) {
        List<UUID> ids = rs.stream().map(ReturnRequest::getId).toList();
        Map<UUID, ReturnLabel> labelBy = labels.currentFor(ids);
        Map<UUID, RefundView> refundBy = refunds.viewsFor(ids);
        boolean staff = viewer.role() != Role.CUSTOMER;
        Map<Long, Long> windowCounts = new HashMap<>();
        List<ReturnDto> out = new ArrayList<>();
        for (ReturnRequest r : rs) {
            ReturnLabel label = labelBy.get(r.getId());
            RefundView refund = refundBy.get(r.getId());
            CustomerSummary cs = null;
            if (staff) {
                var c = r.getCustomer();
                long n = windowCounts.computeIfAbsent(c.getId(), returnService::returnsInWindow);
                cs = new CustomerSummary(c.getId(), c.getFullName(), c.getEmail(), c.isFlagged(), n);
            }
            out.add(new ReturnDto(r.getId(), r.getRmaNumber(), r.getOrder().getOrderNumber(), r.getStatus().name(),
                    timelineStep(r.getStatus()), statusLabel(r, refund), r.getVersion(), r.getRefundMethod().name(),
                    r.getApprovedRefundPaise(), r.getItems().stream().mapToLong(ReturnItem::getUnitPricePaise).sum(),
                    r.getOutcome() == null ? null : r.getOutcome().name(), r.getCreatedAt(), r.getUpdatedAt(),
                    r.getItems().stream().map(ReturnViewAssembler::item).toList(),
                    label == null ? null : new LabelSummary(label.getTrackingNumber(),
                            label.isExpiredAt(clock.instant()) && label.getStatus() == LabelStatus.ACTIVE
                                    ? "EXPIRED" : label.getStatus().name(), label.getExpiresAt()),
                    refund == null ? null : refundDto(refund, staff),
                    timeline(r), allowedActions(r, label, refund, viewer), cs,
                    staff ? r.isRequiresFinanceReview() : null,
                    staff && r.getFinanceReviewReason() != null ? r.getFinanceReviewReason().name() : null));
        }
        return out;
    }

    private static ItemDto item(ReturnItem i) {
        var p = i.getOrderItem().getProduct();
        return new ItemDto(i.getId(), i.getOrderItemId(), i.getUnitIndex(), p.getName(), p.getSku(),
                p.getCategory().getCode(), p.getCategory().getName(), i.getStatus().name(), i.getReason().name(),
                i.getReasonNote(), i.getUnitPricePaise(), i.getGrade() == null ? null : i.getGrade().name(),
                i.getDecision() == null ? null : i.getDecision().name(),
                i.getRejectReason() == null ? null : i.getRejectReason().name(), i.getRefundPaise(), i.getInspectedAt());
    }

    private static RefundDto refundDto(RefundView v, boolean staff) {
        List<AttemptDto> attempts = staff ? v.attempts().stream().map(a -> new AttemptDto(a.getId(), a.getAttemptNo(),
                a.getMethod().name(), a.getStatus().name(), a.getDeclineCode(), a.getGatewayIdempotencyKey(),
                a.getCreatedAt(), a.getCompletedAt())).toList() : List.of();
        String method = v.attempts().isEmpty() ? RefundMethod.ORIGINAL_PAYMENT.name()
                : v.attempts().getLast().getMethod().name();
        return new RefundDto(v.refund().getId(), v.refund().getStatus().name(), v.refund().getAmountPaise(), method,
                attempts);
    }

    /** Part 1 §4.4 mapping of internal state to the customer-facing step. */
    static String timelineStep(ReturnStatus s) {
        return switch (s) {
            case INSPECTED, PENDING_FINANCE, REFUND_PENDING, REFUND_FAILED -> "INSPECTED";
            default -> s.name();
        };
    }

    private static String statusLabel(ReturnRequest r, RefundView refund) {
        Long amt = r.getApprovedRefundPaise();
        return switch (r.getStatus()) {
            case REQUESTED -> "Return requested";
            case APPROVED -> "Approved — print your label and ship the parcel";
            case IN_TRANSIT -> "On its way to our returns centre";
            case RECEIVED -> "Received — we're inspecting your items";
            case INSPECTED, PENDING_FINANCE -> "Inspected — your refund of %s is being reviewed".formatted(Money.formatInr(amt));
            case REFUND_PENDING, REFUND_FAILED -> "Your refund of %s is being processed".formatted(Money.formatInr(amt));
            case REFUNDED -> {
                long refunded = r.getItems().stream().filter(i -> i.getStatus() == ItemStatus.REFUNDED).count();
                yield refunded < r.getItems().size()
                        ? "%s refunded for %d of %d items".formatted(Money.formatInr(amt), refunded, r.getItems().size())
                        : "%s refunded to your %s".formatted(Money.formatInr(amt),
                        r.getRefundMethod() == RefundMethod.STORE_CREDIT ? "store credit" : "original payment method");
            }
            case REJECTED -> "Return not accepted";
            case CANCELLED -> r.getCancelReason() != null && r.getCancelReason().name().equals("LABEL_EXPIRED")
                    ? "Cancelled — the label expired" : "Cancelled";
        };
    }

    private List<TimelineEntry> timeline(ReturnRequest r) {
        Map<String, Instant> entered = audit.firstEntered("return", r.getId().toString());
        Map<String, Instant> stepAt = new HashMap<>();
        for (Map.Entry<String, Instant> e : entered.entrySet()) {
            String step = timelineStep(ReturnStatus.valueOf(e.getKey()));
            stepAt.merge(step, e.getValue(), (a, b) -> a.isBefore(b) ? a : b);
        }
        String current = timelineStep(r.getStatus());
        List<String> steps = new ArrayList<>(STEPS);
        String terminal = switch (r.getStatus()) {
            case REJECTED -> "REJECTED";
            case CANCELLED -> "CANCELLED";
            default -> "REFUNDED";
        };
        if (r.getStatus() == ReturnStatus.CANCELLED) {
            steps.removeIf(s -> !stepAt.containsKey(s)); // show only what happened before cancellation
        }
        steps.add(terminal);
        int currentIdx = steps.indexOf(current);
        List<TimelineEntry> out = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            String s = steps.get(i);
            Instant at = stepAt.get(s);
            if (s.equals("IN_TRANSIT") && at == null && i < currentIdx) {
                continue; // parcel scanned without "mark as shipped": step skipped
            }
            String state = i < currentIdx ? "done" : i == currentIdx
                    ? (r.getStatus().isTerminal() ? "done" : "current") : "upcoming";
            out.add(new TimelineEntry(s, STEP_LABELS.get(s), at, state));
        }
        return out;
    }

    private List<String> allowedActions(ReturnRequest r, ReturnLabel label, RefundView refund, Actor viewer) {
        List<String> a = new ArrayList<>();
        Role role = viewer.role();
        boolean admin = role == Role.ADMIN;
        ReturnStatus s = r.getStatus();
        switch (role) {
            case CUSTOMER -> {
                if (s == ReturnStatus.APPROVED && label != null && label.getStatus() == LabelStatus.ACTIVE
                        && !label.isExpiredAt(clock.instant())) {
                    a.add("SHIP");
                }
                if (s == ReturnStatus.REQUESTED || s == ReturnStatus.APPROVED) {
                    a.add("CANCEL");
                }
            }
            default -> {
                if ((role == Role.WAREHOUSE_AGENT || admin) && s == ReturnStatus.RECEIVED) {
                    a.add("INSPECT");
                    if (r.getItems().stream().noneMatch(i -> i.getStatus() == ItemStatus.RECEIVED)) {
                        a.add("COMPLETE_INSPECTION");
                    }
                }
                if (role == Role.FINANCE_REVIEWER || admin) {
                    if (s == ReturnStatus.PENDING_FINANCE) {
                        a.add("FINANCE_APPROVE");
                        a.add("FINANCE_REJECT");
                    }
                    boolean allTerminal = refund != null && refund.attempts().stream()
                            .allMatch(x -> x.getStatus() == AttemptStatus.SUCCEEDED || x.getStatus() == AttemptStatus.FAILED);
                    if (s == ReturnStatus.REFUND_FAILED && allTerminal) {
                        a.add("RETRY_REFUND");
                    }
                    if (s == ReturnStatus.REFUND_PENDING && refund != null && !refund.attempts().isEmpty()) {
                        RefundAttempt last = refund.attempts().getLast();
                        if (last.getMethod() == RefundMethod.ORIGINAL_PAYMENT
                                && (last.getStatus() == AttemptStatus.UNKNOWN || last.getStatus() == AttemptStatus.PENDING)) {
                            a.add("RECHECK_REFUND");
                        }
                    }
                }
                if ((role == Role.OPS_MANAGER || admin) && (s == ReturnStatus.REQUESTED || s == ReturnStatus.APPROVED)) {
                    a.add("CANCEL");
                }
            }
        }
        return a;
    }
}
