package com.returnright.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * One ticked item on the "Which items are you returning?" step.
 *
 * @param itemId {@code itemId} from the order lookup response
 * @param quantity units to return, at least 1
 */
public record SelectedItemRequest(@NotNull UUID itemId, @NotNull @Positive Integer quantity) {}
