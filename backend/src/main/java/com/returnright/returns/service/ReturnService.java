package com.returnright.returns.service;

import com.returnright.api.dto.response.Eligibility;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.repository.ReturnRepository;
import com.returnright.audit.service.AuditService;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import com.returnright.core.util.Json;
import com.returnright.core.infra.Metrics;
import com.returnright.core.config.RrProperties;
import com.returnright.core.enums.CancelReason;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.core.enums.ReturnReason;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.eligibility.EligibilityEngine;
import com.returnright.eligibility.EligibilityEngine.Result;
import com.returnright.eligibility.EligibilityEngine.UnitFacts;
import com.returnright.eligibility.EligibilityEngine.UnitState;
import com.returnright.orders.entity.Customer;
import com.returnright.orders.entity.Order;
import com.returnright.orders.entity.OrderItem;
import com.returnright.orders.repository.CustomerRepository;
import com.returnright.orders.repository.OrderItemRepository;
import com.returnright.orders.repository.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-facing return workflow: eligibility, submit (T1+T2), ship (T3), cancel (T5), flag rule [A-14]. */
@Service
public class ReturnService {

    public record UnitView(OrderItem orderItem, int unitIndex, Result eligibility, UUID activeReturnId) {}

    public record SubmitUnit(long orderItemId, int unitIndex, ReturnReason reason, String note) {}

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CustomerRepository customers;
    private final ReturnRepository returns;
    private final ReturnStateMachine sm;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Json json;
    private final Metrics metrics;
    private final Clock clock;
    private final ZoneId zone;
    private final RrProperties props;

    public ReturnService(OrderRepository orders, OrderItemRepository orderItems, CustomerRepository customers,
                         ReturnRepository returns, ReturnStateMachine sm, AuditService audit, JdbcTemplate jdbc,
                         Json json, Metrics metrics, Clock clock, ZoneId zone, RrProperties props) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.customers = customers;
        this.returns = returns;
        this.sm = sm;
        this.audit = audit;
        this.jdbc = jdbc;
        this.json = json;
        this.metrics = metrics;
        this.clock = clock;
        this.zone = zone;
        this.props = props;
    }

    // ---------------------------------------------------------------------------------------------------
    // Eligibility facts (one query for unit states) + pure engine
    // ---------------------------------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<UnitView> units(Order order, Instant now) {
        List<OrderItem> items = orderItems.findByOrder(order.getId());
        Map<String, Object[]> active = new HashMap<>(); // "itemId:unit" -> [returnId, status, rma]
        if (!items.isEmpty()) {
            jdbc.query("""
                    SELECT ri.order_item_id, ri.unit_index, r.id, r.status, r.rma_number
                    FROM return_items ri JOIN returns r ON r.id = ri.return_id
                    WHERE ri.status <> 'CANCELLED' AND ri.order_item_id = ANY (?)""",
                    rs -> {
                        active.put(rs.getLong(1) + ":" + rs.getInt(2),
                                new Object[] {rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5)});
                    }, (Object) items.stream().map(OrderItem::getId).toArray(Long[]::new));
        }
        List<UnitView> out = new ArrayList<>();
        for (OrderItem oi : items) {
            for (int u = 1; u <= oi.getQuantity(); u++) {
                Object[] a = active.get(oi.getId() + ":" + u);
                UnitState state = UnitState.AVAILABLE;
                String rma = null;
                UUID activeId = null;
                if (a != null) {
                    String st = (String) a[1];
                    state = ReturnStatus.valueOf(st).isTerminal() ? UnitState.ALREADY_RETURNED : UnitState.IN_ACTIVE_RETURN;
                    rma = (String) a[2];
                    activeId = (UUID) a[0];
                }
                var cat = oi.getProduct().getCategory();
                UnitFacts facts = new UnitFacts(order.getOrderNumber(), order.getStatus().name(), order.getDeliveredAt(),
                        cat.getCode(), cat.getName(), cat.isReturnable(), cat.getReturnWindowDays(), state, rma);
                out.add(new UnitView(oi, u, EligibilityEngine.evaluate(facts, now, zone), activeId));
            }
        }
        return out;
    }

    /** A customer's orders, newest first. */
    @Transactional(readOnly = true)
    public List<Order> ordersForCustomer(long customerId) {
        return orders.findByCustomer(customerId);
    }

    public Order orderForCustomer(String orderNumber, long customerId) {
        Order o = orders.findByOrderNumber(orderNumber).orElseThrow(() -> ApiException.notFound("Order"));
        if (!Objects.equals(o.getCustomer().getId(), customerId)) {
            throw ApiException.notFound("Order");
        }
        return o;
    }

    // ---------------------------------------------------------------------------------------------------
    // T1 + T2
    // ---------------------------------------------------------------------------------------------------
    @Transactional
    public ReturnRequest submit(Actor actor, long customerId, String orderNumber, RefundMethod method,
                                List<SubmitUnit> requested) {
        if (requested == null || requested.isEmpty()) {
            throw ApiException.unprocessable("EMPTY_RETURN", "Select at least one item to return.", Map.of());
        }
        Set<String> seen = new HashSet<>();
        for (SubmitUnit s : requested) {
            if (!seen.add(s.orderItemId() + ":" + s.unitIndex())) {
                throw ApiException.unprocessable("VALIDATION_ERROR", "The same item was selected twice.", Map.of());
            }
        }
        // Lock the customer row: concurrent submits count correctly for the flag rule (F-14)
        Customer customer = customers.findForUpdate(customerId).orElseThrow(() -> ApiException.notFound("Customer"));
        Order order = orderForCustomer(orderNumber, customerId);
        Instant now = clock.instant(); // same reading for eligibility and returns.created_at (F-12)

        Map<String, UnitView> byKey = new LinkedHashMap<>();
        for (UnitView v : units(order, now)) {
            byKey.put(v.orderItem().getId() + ":" + v.unitIndex(), v);
        }
        List<Map<String, Object>> failures = new ArrayList<>();
        List<ReturnStateMachine.NewUnit> newUnits = new ArrayList<>();
        for (SubmitUnit s : requested) {
            UnitView v = byKey.get(s.orderItemId() + ":" + s.unitIndex());
            if (v == null) {
                failures.add(Map.of("order_item_id", s.orderItemId(), "unit_index", s.unitIndex(),
                        "reason_code", "NOT_FOUND", "message", "This item isn't part of the order."));
                continue;
            }
            metrics.eligibility(v.eligibility().eligible(), v.eligibility().reasonCode().name());
            if (!v.eligibility().eligible()) {
                failures.add(Map.of("order_item_id", s.orderItemId(), "unit_index", s.unitIndex(),
                        "reason_code", v.eligibility().reasonCode().name(), "message", v.eligibility().message()));
                continue;
            }
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("eligible", true);
            snap.put("reason_code", v.eligibility().reasonCode().name());
            snap.put("rule_version", v.eligibility().ruleVersion());
            snap.put("details", v.eligibility().details());
            String note = s.note() == null || s.note().isBlank() ? null : s.note().strip();
            newUnits.add(new ReturnStateMachine.NewUnit(v.orderItem(), s.unitIndex(), s.reason(), note, json.write(snap)));
        }
        if (!failures.isEmpty()) { // [A-06] the whole request is rejected, with per-item reasons
            throw ApiException.unprocessable("ITEM_NOT_ELIGIBLE", "Some selected items can't be returned.",
                    Map.of("items", failures));
        }
        ReturnRequest r = sm.submit(order, customer, method, newUnits, actor);
        evaluateFlag(customer, now, actor);
        return r;
    }

    /** [A-14] More than N return requests (incl. cancelled) in a rolling window flags the customer. Review only. */
    private void evaluateFlag(Customer customer, Instant now, Actor actor) {
        long count = returns.countCreatedBetween(customer.getId(), now.minus(Duration.ofDays(props.flagWindowDays())), now);
        if (count > props.flagThreshold() && !customer.isFlagged()) {
            String reason = "%d return requests in %d days".formatted(count, props.flagWindowDays());
            customer.flag(now, reason);
            audit.record(actor, "customer", customer.getId().toString(), "CUSTOMER_FLAGGED", "UNFLAGGED", "FLAGGED",
                    null, null, Map.of("returns_in_window", count));
            metrics.customerFlagged();
        }
    }

    @Transactional
    public ReturnRequest ship(Actor actor, UUID id, int version) {
        return sm.apply(id, ReturnEvent.MARK_SHIPPED, actor, version, null);
    }

    @Transactional
    public ReturnRequest cancel(Actor actor, UUID id, Integer version, CancelReason reason) {
        return sm.apply(id, ReturnEvent.CANCEL, actor, version, new ReturnStateMachine.CancelPayload(reason));
    }

    @Transactional
    public void clearFlag(Actor actor, long customerId, String note) {
        Customer c = customers.findForUpdate(customerId).orElseThrow(() -> ApiException.notFound("Customer"));
        if (!c.isFlagged()) {
            return;
        }
        c.clearFlag();
        audit.record(actor, "customer", c.getId().toString(), "FLAG_CLEARED", "FLAGGED", "UNFLAGGED", null, null,
                Map.of("note", note));
    }

    @Transactional(readOnly = true)
    public ReturnRequest get(UUID id) {
        return returns.findById(id).orElseThrow(() -> ApiException.notFound("Return"));
    }

    @Transactional(readOnly = true)
    public ReturnRequest getForCustomer(UUID id, long customerId) {
        ReturnRequest r = get(id);
        if (!Objects.equals(r.getCustomer().getId(), customerId)) {
            throw ApiException.notFound("Return");
        }
        return r;
    }

    public long returnsInWindow(long customerId) {
        Instant now = clock.instant();
        return returns.countCreatedBetween(customerId, now.minus(Duration.ofDays(props.flagWindowDays())), now);
    }
}
