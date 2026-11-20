package com.returnright.api.controller;

import com.returnright.api.support.RequestSupport;
import com.returnright.api.mapper.ReturnViewAssembler;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import com.returnright.core.infra.IdempotencyService;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.inspection.service.InspectionService;
import com.returnright.api.dto.request.InspectionRequest;
import com.returnright.api.dto.response.ListResponse;
import com.returnright.api.dto.response.ReturnDto;
import com.returnright.api.dto.request.ScanRequest;
import com.returnright.api.dto.response.ScanResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Warehouse Agent persona. */
@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseController {

    private final InspectionService inspection;
    private final ReturnViewAssembler views;
    private final IdempotencyService idempotency;

    public WarehouseController(InspectionService inspection, ReturnViewAssembler views,
                               IdempotencyService idempotency) {
        this.inspection = inspection;
        this.views = views;
        this.idempotency = idempotency;
    }

    @PostMapping("/scan")
    public ScanResponse scan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScanRequest req) {
        Actor a = RequestSupport.actor(jwt);
        InspectionService.ScanResult r = inspection.scan(a, req.qrToken());
        return new ScanResponse(r.alreadyReceived(), r.message(), views.one(r.returnId(), a));
    }

    @GetMapping("/returns")
    public ListResponse<ReturnDto> queue(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "RECEIVED") String status,
                                         @RequestParam(defaultValue = "100") int limit) {
        return Queues.list(views, RequestSupport.actor(jwt), status, limit);
    }

    @GetMapping("/returns/{id}")
    public ResponseEntity<ReturnDto> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ReturnDto dto = views.one(id, RequestSupport.actor(jwt));
        return ResponseEntity.ok().header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
    }

    @PutMapping("/returns/{id}/items/{itemId}/inspection")
    public ResponseEntity<ReturnDto> inspect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @PathVariable UUID itemId, @RequestHeader("If-Match") String ifMatch,
                                             @Valid @RequestBody InspectionRequest req) {
        Actor a = RequestSupport.actor(jwt);
        inspection.inspectItem(a, id, itemId, RequestSupport.version(ifMatch), req.decision(), req.grade(), req.rejectReason(),
                req.notes());
        ReturnDto dto = views.one(id, a);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
    }

    @PostMapping("/returns/{id}/complete-inspection")
    public ResponseEntity<?> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @RequestHeader("Idempotency-Key") String key,
                                      @RequestHeader("If-Match") String ifMatch) {
        Actor a = RequestSupport.actor(jwt);
        int version = RequestSupport.version(ifMatch);
        return idempotency.run(a.id() + ":POST /warehouse/returns/{id}/complete-inspection", key,
                Map.of("id", id, "version", version), "POST /warehouse/returns/{id}/complete-inspection", () -> {
                    inspection.complete(a, id, version);
                    ReturnDto dto = views.one(id, a);
                    return ResponseEntity.status(Queues.refundHttpStatus(dto))
                            .header(HttpHeaders.ETAG, RequestSupport.etag(dto.version())).body(dto);
                });
    }

    /** Shared list helper for persona queues. */
    static final class Queues {
        private Queues() {}

        static ListResponse<ReturnDto> list(ReturnViewAssembler views, Actor a, String csv, int limit) {
            List<ReturnStatus> statuses;
            try {
                statuses = Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                        .map(ReturnStatus::valueOf).toList();
            } catch (IllegalArgumentException e) {
                throw ApiException.unprocessable("VALIDATION_ERROR", "Unknown status filter.", Map.of());
            }
            return views.byStatus(statuses, limit, a);
        }

        /** 202 while the refund outcome is still pending/unknown, 200 when final (contract). */
        static int refundHttpStatus(ReturnDto dto) {
            return "REFUND_PENDING".equals(dto.status()) ? 202 : 200;
        }
    }
}
