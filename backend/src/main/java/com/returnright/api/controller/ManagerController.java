package com.returnright.api.controller;

import com.returnright.api.support.RequestSupport;
import com.returnright.api.mapper.ReturnViewAssembler;
import com.returnright.analytics.service.AnalyticsService;
import com.returnright.audit.service.AuditService;
import com.returnright.core.model.Actor;
import com.returnright.core.infra.IdempotencyService;
import com.returnright.core.enums.CancelReason;
import com.returnright.ledger.service.LedgerService;
import com.returnright.returns.service.ReturnService;
import com.returnright.api.dto.response.ListResponse;
import com.returnright.api.dto.request.RequiredNoteRequest;
import com.returnright.api.dto.response.ReturnDto;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations Manager persona: analytics, flags, audit, reconciliation. */
@RestController
public class ManagerController {

    private final AnalyticsService analytics;
    private final ReturnService returns;
    private final AuditService audit;
    private final LedgerService ledger;
    private final ReturnViewAssembler views;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public ManagerController(AnalyticsService analytics, ReturnService returns, AuditService audit, LedgerService ledger,
                             ReturnViewAssembler views, IdempotencyService idempotency, Clock clock) {
        this.analytics = analytics;
        this.returns = returns;
        this.audit = audit;
        this.ledger = ledger;
        this.views = views;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @GetMapping("/api/v1/analytics/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "30") int days) {
        return analytics.overview(days);
    }

    @GetMapping("/api/v1/analytics/flagged-customers")
    public ListResponse<Map<String, Object>> flagged() {
        List<Map<String, Object>> list = analytics.flaggedCustomers();
        return new ListResponse<>(list, list.size());
    }

    @PostMapping("/api/v1/ops/customers/{id}/clear-flag")
    public ResponseEntity<?> clearFlag(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                       @RequestHeader("Idempotency-Key") String key,
                                       @Valid @RequestBody RequiredNoteRequest body) {
        Actor a = RequestSupport.actor(jwt);
        return idempotency.run(a.id() + ":POST /ops/customers/{id}/clear-flag", key, Map.of("id", id, "note", body.note()),
                "POST /ops/customers/{id}/clear-flag", () -> {
                    returns.clearFlag(a, id, body.note());
                    return ResponseEntity.ok(Map.of("customer_id", id, "is_flagged", false));
                });
    }

    @PostMapping("/api/v1/ops/returns/{id}/cancel")
    public ReturnDto opsCancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @RequestHeader("If-Match") String ifMatch) {
        Actor a = RequestSupport.actor(jwt);
        returns.cancel(a, id, RequestSupport.version(ifMatch), CancelReason.OPS);
        return views.one(id, a);
    }

    @GetMapping("/api/v1/ops/audit")
    public ListResponse<AuditService.Entry> audit(@RequestParam(name = "entity_id", required = false) String entityId,
                                                  @RequestParam(name = "correlation_id", required = false) String cid,
                                                  @RequestParam(defaultValue = "50") int limit) {
        List<AuditService.Entry> list = audit.search(entityId, cid, limit);
        return new ListResponse<>(list, list.size());
    }

    @GetMapping("/api/v1/ops/reconciliation")
    public Map<String, Object> reconciliation() {
        List<LedgerService.Check> checks = ledger.reconcile(15);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("run_at", clock.instant());
        out.put("ok", checks.stream().allMatch(c -> c.mismatches() == 0));
        out.put("checks", checks);
        out.put("account_balances_paise", ledger.accountBalances());
        return out;
    }
}
