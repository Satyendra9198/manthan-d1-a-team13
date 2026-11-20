package com.returnright.web;

import com.returnright.core.enums.Decision;
import com.returnright.core.enums.Grade;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.RejectReason;
import com.returnright.core.enums.ReturnReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response records (docs/api/contract.md). JSON is snake_case globally. */
public final class Dtos {

    private Dtos() {}

    // ----- requests -----
    public record CustomerLookupRequest(@NotBlank @Size(max = 40) String orderNumber,
                                        @NotBlank @Email @Size(max = 254) String email) {}

    public record StaffLoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {}

    public record SubmitItem(@NotNull Long orderItemId, @NotNull @Min(1) Integer unitIndex, @NotNull ReturnReason reason,
                             @Size(max = 500) String note) {}

    public record SubmitReturnRequest(@NotBlank @Size(max = 40) String orderNumber, RefundMethod refundMethod,
                                      @NotEmpty @Size(max = 50) List<@Valid SubmitItem> items) {}

    public record ScanRequest(@NotBlank @Size(max = 64) String qrToken) {}

    public record InspectionRequest(@NotNull Decision decision, Grade grade, RejectReason rejectReason,
                                    @Size(max = 1000) String notes) {}

    public record NoteRequest(@Size(max = 1000) String note) {}

    public record RequiredNoteRequest(@NotBlank @Size(max = 1000) String note) {}

    public record RetryRequest(RefundMethod method) {}

    // ----- responses -----
    public record ItemDto(UUID id, long orderItemId, int unitIndex, String productName, String sku, String categoryCode,
                          String categoryName, String status, String reason, String reasonNote, long unitPricePaise,
                          String grade, String decision, String rejectReason, Long refundPaise, Instant inspectedAt) {}

    public record TimelineEntry(String step, String label, Instant at, String state) {}

    public record AttemptDto(UUID id, int attemptNo, String method, String status, String declineCode,
                             String gatewayIdempotencyKey, Instant createdAt, Instant completedAt) {}

    public record RefundDto(UUID id, String status, long amountPaise, String method, List<AttemptDto> attempts) {}

    public record LabelSummary(String trackingNumber, String status, Instant expiresAt) {}

    public record CustomerSummary(long id, String name, String email, boolean isFlagged,
                                  @JsonProperty("returns_30d") long returns30d) {}

    public record ReturnDto(UUID id, String rmaNumber, String orderNumber, String status, String timelineStep,
                            String statusLabel, int version, String refundMethod, Long approvedRefundPaise,
                            long requestedRefundPaise, String outcome, Instant createdAt, Instant updatedAt,
                            List<ItemDto> items, LabelSummary label, RefundDto refund, List<TimelineEntry> timeline,
                            List<String> allowedActions, CustomerSummary customer, Boolean requiresFinanceReview,
                            String financeReviewReason) {}

    public record ListResponse<T>(List<T> items, long total) {}

    public record ScanResponse(boolean alreadyReceived, String message, @JsonProperty("return") ReturnDto returnDto) {}

    public record Eligibility(boolean eligible, String reasonCode, String message, Map<String, Object> details) {}

    public record UnitDto(long orderItemId, int unitIndex, String productName, String sku, String categoryCode,
                          String categoryName, long pricePaise, Eligibility eligibility, UUID activeReturnId) {}

    public record OrderSummary(String orderNumber, String status, Instant placedAt, Instant deliveredAt, long totalPaise,
                               int itemCount, int returnableUnits) {}

    public record OrderDetail(String orderNumber, String status, Instant placedAt, Instant deliveredAt,
                              String paymentMethod, long totalPaise, List<UnitDto> units) {}

    public record LabelDto(String rmaNumber, String trackingNumber, String qrToken, String qrSvg, String status,
                           Instant issuedAt, Instant expiresAt, Map<String, String> shipTo, String customerName,
                           Map<String, String> returnFrom, List<Map<String, Object>> items) {}
}
