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

public record ItemDto(UUID id, long orderItemId, int unitIndex, String productName, String sku, String categoryCode,
                      String categoryName, String status, String reason, String reasonNote, long unitPricePaise,
                      String grade, String decision, String rejectReason, Long refundPaise, Instant inspectedAt) {}
