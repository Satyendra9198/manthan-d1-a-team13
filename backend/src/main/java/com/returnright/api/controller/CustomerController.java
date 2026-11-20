package com.returnright.api.controller;

import com.returnright.api.support.RequestSupport;
import com.returnright.labels.service.LabelService;
import com.returnright.api.dto.response.LabelDto;
import com.returnright.api.mapper.ReturnViewAssembler;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import com.returnright.core.infra.IdempotencyService;
import com.returnright.core.config.RrProperties;
import com.returnright.core.enums.CancelReason;
import com.returnright.core.enums.RefundMethod;
import com.returnright.labels.repository.ReturnLabelRepository;
import com.returnright.labels.entity.ReturnLabel;
import com.returnright.notifications.service.NotificationService;
import com.returnright.orders.entity.Order;
import com.returnright.returns.entity.ReturnRequest;
import com.returnright.returns.service.ReturnService;
import com.returnright.returns.service.ReturnService.SubmitUnit;
import com.returnright.returns.service.ReturnService.UnitView;
import com.returnright.api.dto.response.Eligibility;
import com.returnright.api.dto.response.ListResponse;
import com.returnright.api.dto.response.OrderDetail;
import com.returnright.api.dto.response.OrderSummary;
import com.returnright.api.dto.response.ReturnDto;
import com.returnright.api.dto.request.SubmitReturnRequest;
import com.returnright.api.dto.response.UnitDto;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer persona (role CUSTOMER). Every query is scoped to the caller's customer id; misses are 404. */
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    private final ReturnService returns;
    private final ReturnViewAssembler views;
    private final IdempotencyService idempotency;
    private final LabelService labels;
    private final NotificationService notifications;
    private final Clock clock;
    private final RrProperties props;

    public CustomerController(ReturnService returns, ReturnViewAssembler views, IdempotencyService idempotency, LabelService labels,
                              NotificationService notifications, Clock clock, RrProperties props) {
        this.returns = returns;
        this.views = views;
        this.idempotency = idempotency;
        this.labels = labels;
        this.notifications = notifications;
        this.clock = clock;
        this.props = props;
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ListResponse<OrderSummary> orders(@AuthenticationPrincipal Jwt jwt) {
        Actor a = RequestSupport.actor(jwt);
        List<OrderSummary> list = returns.ordersForCustomer(a.customerId()).stream().map(o -> {
            List<UnitView> units = returns.units(o, clock.instant());
            int returnable = (int) units.stream().filter(u -> u.eligibility().eligible()).count();
            return new OrderSummary(o.getOrderNumber(), o.getStatus().name(), o.getPlacedAt(), o.getDeliveredAt(),
                    o.getTotalPaise(), units.size(), returnable);
        }).toList();
        return new ListResponse<>(list, list.size());
    }

    @GetMapping("/orders/{orderNumber}")
    @Transactional(readOnly = true)
    public OrderDetail order(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber) {
        Actor a = RequestSupport.actor(jwt);
        Order o = returns.orderForCustomer(orderNumber.toUpperCase(), a.customerId());
        List<UnitDto> units = returns.units(o, clock.instant()).stream().map(u -> {
            var p = u.orderItem().getProduct();
            var e = u.eligibility();
            return new UnitDto(u.orderItem().getId(), u.unitIndex(), p.getName(), p.getSku(), p.getCategory().getCode(),
                    p.getCategory().getName(), u.orderItem().getUnitPricePaise(),
                    new Eligibility(e.eligible(), e.reasonCode().name(), e.message(), e.details()), u.activeReturnId());
        }).toList();
        return new OrderDetail(o.getOrderNumber(), o.getStatus().name(), o.getPlacedAt(), o.getDeliveredAt(),
                o.getPaymentMethod().name(), o.getTotalPaise(), units);
    }

    @PostMapping("/returns")
    public ResponseEntity<?> submit(@AuthenticationPrincipal Jwt jwt,
                                    @RequestHeader("Idempotency-Key") String key,
                                    @Valid @RequestBody SubmitReturnRequest req) {
        Actor a = RequestSupport.actor(jwt);
        return idempotency.run(a.id() + ":POST /customer/returns", key, req, "POST /customer/returns", () -> {
            List<SubmitUnit> units = req.items().stream()
                    .map(i -> new SubmitUnit(i.orderItemId(), i.unitIndex(), i.reason(), i.note())).toList();
            ReturnRequest r = returns.submit(a, a.customerId(), req.orderNumber().strip().toUpperCase(),
                    req.refundMethod() == null ? RefundMethod.ORIGINAL_PAYMENT : req.refundMethod(), units);
            ReturnDto dto = views.one(r.getId(), a);
            return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
        });
    }

    @GetMapping("/returns")
    public ListResponse<ReturnDto> list(@AuthenticationPrincipal Jwt jwt) {
        Actor a = RequestSupport.actor(jwt);
        return views.forCustomer(a.customerId(), a);
    }

    @GetMapping("/returns/{id}")
    public ResponseEntity<ReturnDto> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ReturnDto dto = views.one(id, RequestSupport.actor(jwt));
        return ResponseEntity.ok().header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
    }

    @GetMapping("/returns/{id}/label")
    @Transactional(readOnly = true)
    public LabelDto label(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Actor a = RequestSupport.actor(jwt);
        ReturnRequest r = returns.getForCustomer(id, a.customerId());
        ReturnLabel l = labels.current(r.getId()).orElseThrow(() -> ApiException.notFound("Label"));
        var c = r.getCustomer();
        var w = props.warehouse();
        Map<String, String> shipTo = new LinkedHashMap<>();
        shipTo.put("name", w.name());
        shipTo.put("line1", w.line1());
        shipTo.put("city", w.city());
        shipTo.put("pincode", w.pincode());
        Map<String, String> from = new LinkedHashMap<>();
        from.put("name", c.getFullName());
        from.put("line1", c.getAddressLine1());
        from.put("city", c.getCity());
        from.put("pincode", c.getPincode());
        List<Map<String, Object>> items = r.getItems().stream().map(i -> Map.<String, Object>of(
                "product_name", i.getOrderItem().getProduct().getName(), "unit_index", i.getUnitIndex())).toList();
        String status = l.getStatus().name().equals("ACTIVE") && l.isExpiredAt(clock.instant()) ? "EXPIRED"
                : l.getStatus().name();
        return new LabelDto(r.getRmaNumber(), l.getTrackingNumber(), l.getQrToken(),
                LabelService.qrSvgDataUri(l.getQrToken()), status, l.getIssuedAt(), l.getExpiresAt(), shipTo,
                c.getFullName(), from, items);
    }

    @PostMapping("/returns/{id}/ship")
    public ResponseEntity<ReturnDto> ship(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @RequestHeader("If-Match") String ifMatch) {
        Actor a = RequestSupport.actor(jwt);
        returns.ship(a, id, RequestSupport.version(ifMatch));
        ReturnDto dto = views.one(id, a);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
    }

    @PostMapping("/returns/{id}/cancel")
    public ResponseEntity<ReturnDto> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @RequestHeader("If-Match") String ifMatch) {
        Actor a = RequestSupport.actor(jwt);
        returns.cancel(a, id, RequestSupport.version(ifMatch), CancelReason.CUSTOMER);
        ReturnDto dto = views.one(id, a);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
    }

    @GetMapping("/notifications")
    public ListResponse<NotificationService.Notification> notifications(@AuthenticationPrincipal Jwt jwt) {
        List<NotificationService.Notification> list = notifications.forCustomer(RequestSupport.actor(jwt).customerId(), 50);
        return new ListResponse<>(list, list.size());
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.markAllRead(RequestSupport.actor(jwt).customerId());
        return ResponseEntity.noContent().build();
    }
}
