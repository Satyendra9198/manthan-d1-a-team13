package com.returnright.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Items the customer wants to return. The order number and email are sent again because the
 * customer has no login; they prove ownership on every wizard step.
 */
public record ItemSelectionRequest(
    @NotBlank @Size(max = 32) String orderNumber,
    @NotBlank @Email @Size(max = 254) String email,
    @NotEmpty @Size(max = 50) List<@Valid @NotNull SelectedItemRequest> items) {}
