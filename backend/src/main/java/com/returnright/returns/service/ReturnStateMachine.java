package com.returnright.returns.service;

import com.returnright.labels.service.LabelService;
import com.returnright.returns.entity.ReturnItem;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.repository.ReturnRepository;
import static com.returnright.core.enums.ReturnEvent.*;
import static com.returnright.core.enums.ReturnStatus.*;

import com.returnright.audit.service.AuditService;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import com.returnright.core.infra.Metrics;
import com.returnright.core.util.Money;
import com.returnright.core.infra.OutboxService;
import com.returnright.core.config.RrProperties;
import com.returnright.core.enums.CancelReason;
import com.returnright.core.enums.Decision;
import com.returnright.core.enums.FinanceReviewReason;
import com.returnright.core.enums.Grade;
import com.returnright.core.enums.ItemStatus;
import com.returnright.core.enums.LabelStatus;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.RejectReason;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.core.enums.ReturnOutcome;
import com.returnright.core.enums.ReturnReason;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.core.enums.Role;
import com.returnright.labels.repository.ReturnLabelRepository;
import com.returnright.labels.entity.ReturnLabel;
import com.returnright.notifications.service.NotificationService;
import com.returnright.orders.entity.Customer;
import com.returnright.orders.entity.Order;
import com.returnright.orders.entity.OrderItem;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * THE single place that changes return / return-item status (CLAUDE.md, Part 1 §4.5).
 * Order of checks in {@link #apply}: row lock -> ownership -> If-Match version -> transition exists -> role -> guards.
 * DB effects run in the caller's transaction; the row is flushed after every transition so the DB trigger
 * trg_returns_valid_transition [BR-6] sees each step.
 */
@Service
public class ReturnStateMachine {

    // ----- payloads -----
    public record ScanPayload(ReturnLabel label) {}

    public record CancelPayload(CancelReason reason) {}

    public record InspectPayload(UUID itemId, Decision decision, Grade grade, RejectReason rejectReason, String notes) {}

    public record NewUnit(OrderItem orderItem, int unitIndex, ReturnReason reason, String note, String eligibilityJson) {}

    private record Ctx(ReturnRequest r, Actor actor, Object payload, Instant now) {}

    /** One candidate transition; for ROUTE the first candidate whose {@code when} holds wins (T10-T12). */
    record Transition(ReturnStatus to, Set<Role> roles, Predicate<Ctx> when) {}

    private record Key(ReturnStatus from, ReturnEvent event) {}

    private static final Set<Role> C = EnumSet.of(Role.CUSTOMER);
    private static final Set<Role> CM = EnumSet.of(Role.CUSTOMER, Role.OPS_MANAGER);
    private static final Set<Role> W = EnumSet.of(Role.WAREHOUSE_AGENT);
    private static final Set<Role> F = EnumSet.of(Role.FINANCE_REVIEWER);
    private static final Set<Role> S = EnumSet.of(Role.SYSTEM);

    private final Map<Key, List<Transition>> table = new LinkedHashMap<>();

    private final ReturnRepository returns;
    private final EntityManager em;
    private final LabelService labels;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OutboxService outbox;
    private final Metrics metrics;
    private final Clock clock;
    private final RrProperties props;

    public ReturnStateMachine(ReturnRepository returns, EntityManager em, LabelService labels,
                              NotificationService notifications, AuditService audit, OutboxService outbox,
                              Metrics metrics, Clock clock, RrProperties props) {
        this.returns = returns;
        this.em = em;
        this.labels = labels;
        this.notifications = notifications;
        this.audit = audit;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
        this.props = props;
        defineTable();
    }

    /** Part 1 §4.3. Keep in sync with V2 trigger (TransitionTableParityTest). */
    private void defineTable() {
        add(REQUESTED, AUTO_APPROVE, APPROVED, S);                                   // T2
        add(APPROVED, MARK_SHIPPED, IN_TRANSIT, C);                                  // T3
        add(APPROVED, SCAN_RECEIVE, RECEIVED, W);                                    // T4
        add(IN_TRANSIT, SCAN_RECEIVE, RECEIVED, W);                                  // T4
        add(REQUESTED, CANCEL, CANCELLED, CM);                                       // T5
        add(APPROVED, CANCEL, CANCELLED, CM);                                        // T5
        add(APPROVED, LABEL_EXPIRE, CANCELLED, S);                                   // T6
        add(RECEIVED, INSPECT_ITEM, RECEIVED, W);                                    // T7
        add(RECEIVED, REGRADE_ITEM, RECEIVED, W);                                    // T8
        add(RECEIVED, COMPLETE_INSPECTION, INSPECTED, W);                            // T9
        addWhen(INSPECTED, ROUTE, REJECTED, S, c -> c.r().getApprovedRefundPaise() == 0);           // T10
        addWhen(INSPECTED, ROUTE, PENDING_FINANCE, S, c -> financeReason(c.r()) != null);          // T11
        addWhen(INSPECTED, ROUTE, REFUND_PENDING, S, c -> true);                                   // T12
        add(PENDING_FINANCE, FINANCE_APPROVE, REFUND_PENDING, F);                    // T13
        add(PENDING_FINANCE, FINANCE_REJECT, REJECTED, F);                           // T14
        add(REFUND_PENDING, REFUND_SUCCEEDED, REFUNDED, S);                          // T15
        add(REFUND_PENDING, ReturnEvent.REFUND_FAILED, ReturnStatus.REFUND_FAILED, S); // T16
        add(ReturnStatus.REFUND_FAILED, RETRY_REFUND, REFUND_PENDING, F);            // T17
    }

    private void add(ReturnStatus from, ReturnEvent ev, ReturnStatus to, Set<Role> roles) {
        addWhen(from, ev, to, roles, null);
    }

    private void addWhen(ReturnStatus from, ReturnEvent ev, ReturnStatus to, Set<Role> roles, Predicate<Ctx> when) {
        table.computeIfAbsent(new Key(from, ev), k -> new java.util.ArrayList<>()).add(new Transition(to, roles, when));
    }

    /** Status-changing (from>to) pairs; must equal the V2 trigger list [BR-6]. */
    public Set<String> allowedPairs() {
        return table.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .filter(t -> t.to() != e.getKey().from())
                        .map(t -> e.getKey().from() + ">" + t.to()))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /** True if (status, event) has a transition — used to compute allowed_actions for the UI. */
    public boolean hasTransition(ReturnStatus from, ReturnEvent ev) {
        return table.containsKey(new Key(from, ev));
    }

    // =====================================================================================================
    // T1: create (the only place a return is born) followed by T2 in the same transaction
    // =====================================================================================================
    @Transactional(propagation = Propagation.MANDATORY)
    public ReturnRequest submit(Order order, Customer customer, RefundMethod method, List<NewUnit> units, Actor actor) {
        Instant now = clock.instant();
        String rma = "RMA-%s-%06d".formatted(now.toString().substring(2, 4), returns.nextRmaSeq());
        ReturnRequest r = new ReturnRequest(rma, order, customer, method, now);
        em.persist(r);
        for (NewUnit u : units) {
            ReturnItem item = new ReturnItem(r, u.orderItem(), u.unitIndex(), u.reason(), u.note(), u.eligibilityJson());
            em.persist(item);
            r.getItems().add(item);
        }
        em.flush(); // DB trigger [BR-9] + unique index [BR-8] run here
        audit.record(actor, "return", r.getId().toString(), SUBMIT.name(), null, REQUESTED.name(), null, r.snapshot(),
                Map.of("rma_number", rma, "units", units.size(), "order_number", order.getOrderNumber()));
        for (ReturnItem i : r.getItems()) {
            audit.record(actor, "return_item", i.getId().toString(), SUBMIT.name(), null, ItemStatus.REQUESTED.name(),
                    null, i.snapshot(), Map.of("return_id", r.getId().toString()));
        }
        emit(r, null, REQUESTED, SUBMIT);
        afterCommit(() -> {
            metrics.returnRequested();
            metrics.transition("NONE", REQUESTED.name(), SUBMIT.name());
        });
        return apply(r.getId(), AUTO_APPROVE, Actor.system("auto-approve"), null, null);
    }

    // =====================================================================================================
    // The single transition function
    // =====================================================================================================
    @Transactional(propagation = Propagation.MANDATORY)
    public ReturnRequest apply(UUID returnId, ReturnEvent event, Actor actor, Integer expectedVersion, Object payload) {
        ReturnRequest r = returns.findForUpdate(returnId).orElseThrow(() -> ApiException.notFound("Return"));
        if (actor.role() == Role.CUSTOMER && !Objects.equals(r.getCustomer().getId(), actor.customerId())) {
            throw ApiException.notFound("Return"); // never leak other customers' data
        }
        if (expectedVersion != null && expectedVersion != r.getVersion()) {
            metrics.conflict("STALE_VERSION");
            throw ApiException.conflict("STALE_VERSION",
                    "This return was updated by someone else. Please reload and try again.",
                    Map.of("current_version", r.getVersion(), "current_state", r.getStatus().name()));
        }
        ReturnStatus from = r.getStatus();
        List<Transition> candidates = table.get(new Key(from, event));
        if (candidates == null) {
            metrics.illegalTransition(from.name(), event.name(), "ILLEGAL_TRANSITION");
            metrics.conflict("ILLEGAL_TRANSITION");
            throw ApiException.conflict("ILLEGAL_TRANSITION", illegalMessage(from, event),
                    Map.of("current_state", from.name(), "event", event.name(), "version", r.getVersion()));
        }
        if (!roleAllowed(candidates.getFirst().roles(), actor.role())) {
            throw new ApiException("FORBIDDEN_ROLE", "Your role can't perform this action.", HttpStatus.FORBIDDEN);
        }
        Instant now = clock.instant();
        Ctx ctx = new Ctx(r, actor, payload, now);
        Transition t = candidates.stream().filter(c -> c.when() == null || c.when().test(ctx)).findFirst()
                .orElseThrow(() -> ApiException.unprocessable("GUARD_FAILED", "Transition guard failed.",
                        Map.of("event", event.name())));

        Map<String, Object> before = r.snapshot();
        Map<String, Object> meta = new LinkedHashMap<>();
        effects(event, t.to(), ctx, meta);

        r.setStatus(t.to());
        r.bumpVersion(now);
        if (t.to().isTerminal()) {
            r.setClosedAt(now);
        }
        em.flush(); // each step hits the DB triggers [BR-4][BR-6]
        audit.record(actor, "return", r.getId().toString(), event.name(), from.name(), t.to().name(), before,
                r.snapshot(), meta);
        if (from != t.to()) {
            emit(r, from, t.to(), event);
        }
        afterCommit(() -> metrics.transition(from.name(), t.to().name(), event.name()));
        return r;
    }

    private static boolean roleAllowed(Set<Role> roles, Role role) {
        if (roles.contains(role)) {
            return true;
        }
        // ADMIN may act for any staff role (not for customers or the system)
        return role == Role.ADMIN && roles.stream().anyMatch(Role::isStaff);
    }

    // =====================================================================================================
    // Effects (same transaction)
    // =====================================================================================================
    private void effects(ReturnEvent event, ReturnStatus to, Ctx c, Map<String, Object> meta) {
        ReturnRequest r = c.r();
        switch (event) {
            case AUTO_APPROVE -> {
                ReturnLabel label = labels.issue(r.getId());
                meta.put("tracking_number", label.getTrackingNumber());
                notify(r, "RETURN_APPROVED", "Return approved",
                        "Your return %s is approved. Print your label and ship it before %s."
                                .formatted(r.getRmaNumber(), label.getExpiresAt().toString().substring(0, 10)));
            }
            case MARK_SHIPPED -> {
                ReturnLabel label = labels.current(r.getId()).orElseThrow(() -> labelError("LABEL_NOT_FOUND"));
                if (label.getStatus() != LabelStatus.ACTIVE || label.isExpiredAt(c.now())) {
                    throw labelError("LABEL_EXPIRED");
                }
                notify(r, "IN_TRANSIT", "Return on its way", "Thanks! We'll let you know when %s arrives."
                        .formatted(r.getRmaNumber()));
            }
            case SCAN_RECEIVE -> {
                ReturnLabel label = ((ScanPayload) c.payload()).label();
                if (label.getStatus() == LabelStatus.VOIDED) {
                    throw labelError("LABEL_VOID");
                }
                if (label.isExpiredAt(c.now())) {
                    throw labelError("LABEL_EXPIRED");
                }
                labels.markUsed(label);
                r.getItems().stream().filter(i -> i.getStatus() == ItemStatus.REQUESTED)
                        .forEach(i -> setItemStatus(i, ItemStatus.RECEIVED, event, c.actor()));
                meta.put("tracking_number", label.getTrackingNumber());
                notify(r, "RECEIVED", "Return received", "We've received %s and are inspecting your items."
                        .formatted(r.getRmaNumber()));
            }
            case CANCEL, LABEL_EXPIRE -> {
                CancelReason reason = event == LABEL_EXPIRE ? CancelReason.LABEL_EXPIRED
                        : ((CancelPayload) c.payload()).reason();
                if (event == LABEL_EXPIRE) {
                    ReturnLabel label = labels.current(r.getId()).orElse(null);
                    if (label == null || !label.isExpiredAt(c.now())) {
                        throw ApiException.unprocessable("GUARD_FAILED", "Label has not expired.", Map.of());
                    }
                }
                labels.close(r.getId(), event == LABEL_EXPIRE ? LabelStatus.EXPIRED : LabelStatus.VOIDED);
                r.getItems().forEach(i -> setItemStatus(i, ItemStatus.CANCELLED, event, c.actor()));
                r.setCancelReason(reason);
                meta.put("cancel_reason", reason.name());
                notify(r, "CANCELLED", "Return cancelled", reason == CancelReason.LABEL_EXPIRED
                        ? "Return %s was cancelled because the label expired.".formatted(r.getRmaNumber())
                        : "Return %s has been cancelled.".formatted(r.getRmaNumber()));
            }
            case INSPECT_ITEM, REGRADE_ITEM -> inspect(event, c, meta);
            case COMPLETE_INSPECTION -> {
                List<ReturnItem> pending = r.getItems().stream()
                        .filter(i -> i.getStatus() == ItemStatus.RECEIVED).toList();
                if (!pending.isEmpty()) {
                    throw ApiException.unprocessable("INSPECTION_INCOMPLETE",
                            "Grade every item before completing the inspection.",
                            Map.of("pending_items", pending.stream().map(i -> i.getId().toString()).toList()));
                }
                long total = r.getItems().stream().filter(i -> i.getDecision() == Decision.ACCEPT)
                        .mapToLong(ReturnItem::getRefundPaise).sum();
                long accepted = r.getItems().stream().filter(i -> i.getDecision() == Decision.ACCEPT).count();
                r.setApprovedRefundPaise(total);
                r.setOutcome(accepted == 0 ? ReturnOutcome.NONE
                        : accepted == r.getItems().size() ? ReturnOutcome.FULL : ReturnOutcome.PARTIAL); // [A-09]
                // The amount and the review requirement are set together so the row never violates [BR-4]
                FinanceReviewReason reason = total == 0 ? null : financeReason(r);
                r.setFinanceReview(reason != null, reason);
                meta.put("approved_refund_paise", total);
            }
            case ROUTE -> {
                if (to == PENDING_FINANCE) {
                    FinanceReviewReason reason = financeReason(r);
                    r.setFinanceReview(true, reason);
                    meta.put("finance_review_reason", reason.name());
                    notify(r, "UNDER_REVIEW", "Refund under review",
                            "Your refund of %s for %s is being reviewed.".formatted(
                                    Money.formatInr(r.getApprovedRefundPaise()), r.getRmaNumber()));
                } else if (to == REJECTED) {
                    notify(r, "REJECTED", "Return not accepted",
                            "We couldn't accept the items in %s after inspection. They'll be sent back to you."
                                    .formatted(r.getRmaNumber()));
                }
            }
            case RETRY_REFUND -> {
                if (c.payload() instanceof RefundMethod m && m != r.getRefundMethod()) { // [A-17] switch after failure
                    meta.put("refund_method_from", r.getRefundMethod().name());
                    meta.put("refund_method_to", m.name());
                    r.setRefundMethod(m);
                }
            }
            case FINANCE_APPROVE, REFUND_FAILED -> { /* refund rows handled by RefundService */ }
            case FINANCE_REJECT -> {
                r.getItems().stream().filter(i -> i.getStatus() == ItemStatus.ACCEPTED).forEach(i -> {
                    i.inspect(Decision.REJECT, i.getGrade(), RejectReason.FINANCE_REJECTED, 0L,
                            i.getInspectionNotes(), null, i.getInspectedAt());
                    setItemStatus(i, ItemStatus.REJECTED, event, c.actor());
                });
                notify(r, "REJECTED", "Refund declined", "After review we're unable to refund %s."
                        .formatted(r.getRmaNumber()));
            }
            case REFUND_SUCCEEDED -> {
                r.getItems().stream().filter(i -> i.getStatus() == ItemStatus.ACCEPTED)
                        .forEach(i -> setItemStatus(i, ItemStatus.REFUNDED, event, c.actor()));
                notify(r, "REFUNDED", "Refund issued", "%s has been refunded to your %s.".formatted(
                        Money.formatInr(r.getApprovedRefundPaise()),
                        r.getRefundMethod() == RefundMethod.STORE_CREDIT ? "store credit" : "original payment method"));
            }
            case SUBMIT -> throw new IllegalStateException("SUBMIT goes through submit()");
        }
    }

    private void inspect(ReturnEvent event, Ctx c, Map<String, Object> meta) {
        InspectPayload p = (InspectPayload) c.payload();
        ReturnItem item = c.r().getItems().stream().filter(i -> i.getId().equals(p.itemId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("Return item"));
        boolean firstTime = item.getStatus() == ItemStatus.RECEIVED;
        if (event == INSPECT_ITEM && !firstTime || event == REGRADE_ITEM && firstTime) {
            throw ApiException.conflict("ILLEGAL_TRANSITION", "This item can't be inspected in its current state.",
                    Map.of("item_status", item.getStatus().name()));
        }
        if (p.decision() == Decision.ACCEPT && p.grade() == null) {
            throw ApiException.unprocessable("VALIDATION_ERROR", "A grade is required to accept an item.",
                    Map.of("fields", List.of(Map.of("loc", List.of("body", "grade"), "msg", "required"))));
        }
        if (p.decision() == Decision.REJECT && (p.rejectReason() == null || p.rejectReason() == RejectReason.FINANCE_REJECTED)) {
            throw ApiException.unprocessable("VALIDATION_ERROR", "Choose a reason for rejecting the item.",
                    Map.of("fields", List.of(Map.of("loc", List.of("body", "reject_reason"), "msg", "required"))));
        }
        long refund = p.decision() == Decision.ACCEPT ? Money.gradeRefundPaise(item.getUnitPricePaise(), p.grade()) : 0L;
        Map<String, Object> before = item.snapshot();
        ItemStatus from = item.getStatus();
        ItemStatus to = p.decision() == Decision.ACCEPT ? ItemStatus.ACCEPTED : ItemStatus.REJECTED;
        item.inspect(p.decision(), p.decision() == Decision.ACCEPT ? p.grade() : p.grade(),
                p.decision() == Decision.REJECT ? p.rejectReason() : null, refund, p.notes(), c.actor().userId(), c.now());
        item.setStatus(to);
        audit.record(c.actor(), "return_item", item.getId().toString(), event.name(), from.name(), to.name(), before,
                item.snapshot(), Map.of("return_id", c.r().getId().toString()));
        meta.put("item_id", item.getId().toString());
        meta.put("decision", p.decision().name());
    }

    private void setItemStatus(ReturnItem item, ItemStatus to, ReturnEvent event, Actor actor) {
        ItemStatus from = item.getStatus();
        Map<String, Object> before = item.snapshot();
        item.setStatus(to);
        audit.record(actor, "return_item", item.getId().toString(), event.name(), from.name(), to.name(), before,
                item.snapshot(), Map.of("return_id", item.getRet().getId().toString()));
    }

    /** [A-12][A-13] Per return, after grade adjustment, strictly greater than the threshold; or customer flagged. */
    private FinanceReviewReason financeReason(ReturnRequest r) {
        boolean amount = r.getApprovedRefundPaise() != null && r.getApprovedRefundPaise() > props.financeThresholdPaise();
        boolean flagged = r.getCustomer().isFlagged(); // read live at routing time (F-14)
        if (amount && flagged) {
            return FinanceReviewReason.BOTH;
        }
        return amount ? FinanceReviewReason.AMOUNT : flagged ? FinanceReviewReason.FLAGGED : null;
    }

    private void notify(ReturnRequest r, String template, String title, String body) {
        notifications.notify(r.getCustomer().getId(), r.getId(), template, title, body);
    }

    private void emit(ReturnRequest r, ReturnStatus from, ReturnStatus to, ReturnEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("return_id", r.getId().toString());
        payload.put("rma_number", r.getRmaNumber());
        payload.put("order_number", r.getOrder().getOrderNumber());
        payload.put("customer_id", r.getCustomer().getId());
        payload.put("customer_email", r.getCustomer().getEmail());
        payload.put("customer_name", r.getCustomer().getFullName());
        payload.put("from_state", from == null ? null : from.name());
        payload.put("to_state", to.name());
        payload.put("event", event.name());
        payload.put("approved_refund_paise", r.getApprovedRefundPaise());
        payload.put("refund_method", r.getRefundMethod().name());
        outbox.add("return", r.getId().toString(), "ReturnStatusChanged", payload);
    }

    private static ApiException labelError(String code) {
        String msg = switch (code) {
            case "LABEL_VOID" -> "This label was voided — the return was cancelled. Do not accept the parcel.";
            case "LABEL_EXPIRED" -> "This return label has expired.";
            default -> "Return label not found.";
        };
        return ApiException.conflict(code, msg, Map.of());
    }

    private static String illegalMessage(ReturnStatus from, ReturnEvent event) {
        if (from == CANCELLED && event == SCAN_RECEIVE) {
            return "Do not accept this parcel: the return was cancelled. Send it back to the customer.";
        }
        return "This action isn't possible while the return is %s.".formatted(from.name().toLowerCase().replace('_', ' '));
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
