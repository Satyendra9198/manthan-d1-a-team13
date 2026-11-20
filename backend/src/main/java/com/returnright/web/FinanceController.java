package com.returnright.web;

import com.returnright.core.Actor;
import com.returnright.core.IdempotencyService;
import com.returnright.finance.FinanceService;
import com.returnright.returns.ReturnRepository;
import com.returnright.web.Dtos.ListResponse;
import com.returnright.web.Dtos.NoteRequest;
import com.returnright.web.Dtos.RequiredNoteRequest;
import com.returnright.web.Dtos.RetryRequest;
import com.returnright.web.Dtos.ReturnDto;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Finance Reviewer persona. Approve/reject/retry require Idempotency-Key + If-Match (ADR-001 L1, Part 1 §4.5). */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final FinanceService finance;
    private final ReturnRepository returns;
    private final ReturnViewAssembler views;
    private final IdempotencyService idempotency;

    public FinanceController(FinanceService finance, ReturnRepository returns, ReturnViewAssembler views,
                             IdempotencyService idempotency) {
        this.finance = finance;
        this.returns = returns;
        this.views = views;
        this.idempotency = idempotency;
    }

    @GetMapping("/queue")
    public ListResponse<ReturnDto> queue(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "PENDING_FINANCE,REFUND_FAILED,REFUND_PENDING") String status,
                                         @RequestParam(defaultValue = "100") int limit) {
        return WarehouseController.Queues.list(views, Web.actor(jwt), status, limit);
    }

    @GetMapping("/returns/{id}")
    public ResponseEntity<ReturnDto> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ReturnDto dto = views.one(id, Web.actor(jwt));
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Web.etag(dto.version())).body(dto);
    }

    @PostMapping("/returns/{id}/approve")
    public ResponseEntity<?> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String ifMatch,
                                     @Valid @RequestBody(required = false) NoteRequest body) {
        Actor a = Web.actor(jwt);
        int v = Web.version(ifMatch);
        String note = body == null ? null : body.note();
        return idem(a, key, "approve", id, v, note, () -> finance.approve(a, id, v, note));
    }

    @PostMapping("/returns/{id}/reject")
    public ResponseEntity<?> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String ifMatch,
                                    @Valid @RequestBody RequiredNoteRequest body) {
        Actor a = Web.actor(jwt);
        int v = Web.version(ifMatch);
        return idem(a, key, "reject", id, v, body.note(), () -> finance.reject(a, id, v, body.note()));
    }

    @PostMapping("/returns/{id}/refund/retry")
    public ResponseEntity<?> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String ifMatch,
                                   @RequestBody(required = false) RetryRequest body) {
        Actor a = Web.actor(jwt);
        int v = Web.version(ifMatch);
        var method = body == null ? null : body.method();
        return idem(a, key, "retry", id, v, method, () -> finance.retry(a, id, v, method));
    }

    @PostMapping("/refund-attempts/{attemptId}/recheck")
    public ReturnDto recheck(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        Actor a = Web.actor(jwt);
        UUID returnId = finance.recheck(a, attemptId);
        return views.one(returnId, a);
    }

    private ResponseEntity<?> idem(Actor a, String key, String action, UUID id, int version, Object extra,
                                   Runnable op) {
        String route = "POST /finance/returns/{id}/" + action;
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", id);
        body.put("version", version);
        body.put("extra", extra);
        Supplier<ResponseEntity<?>> run = () -> {
            op.run();
            ReturnDto dto = views.one(id, a);
            return ResponseEntity.status(WarehouseController.Queues.refundHttpStatus(dto))
                    .header(HttpHeaders.ETAG, Web.etag(dto.version())).body(dto);
        };
        return idempotency.run(a.id() + ":" + route, key, body, route, run);
    }
}
