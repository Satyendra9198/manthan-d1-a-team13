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

public record AttemptDto(UUID id, int attemptNo, String method, String status, String declineCode,
                         String gatewayIdempotencyKey, Instant createdAt, Instant completedAt) {}
