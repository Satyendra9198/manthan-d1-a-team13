package com.returnright.model.dto.request;

import com.returnright.model.enums.ReturnReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One item on the return, with the answers from wizard step 3.
 *
 * @param comment optional "Tell us more" text
 */
public record ReturnItemRequest(
    @NotNull UUID itemId,
    @NotNull @Positive Integer quantity,
    @NotNull ReturnReason reason,
    @Size(max = 500) String comment) {}
