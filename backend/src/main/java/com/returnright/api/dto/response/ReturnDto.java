package com.returnright.api.dto.response;

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

public record ReturnDto(UUID id, String rmaNumber, String orderNumber, String status, String timelineStep,
                        String statusLabel, int version, String refundMethod, Long approvedRefundPaise,
                        long requestedRefundPaise, String outcome, Instant createdAt, Instant updatedAt,
                        List<ItemDto> items, LabelSummary label, RefundDto refund, List<TimelineEntry> timeline,
                        List<String> allowedActions, CustomerSummary customer, Boolean requiresFinanceReview,
                        String financeReviewReason) {}
