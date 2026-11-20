package com.returnright.model.dto.request;

import com.returnright.model.enums.ReturnReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReturnItemRequest(
    @NotNull UUID orderItemId,
    @Min(1) int quantity,
    @NotNull ReturnReason reason,
    @Size(max = 500) String note) {}
