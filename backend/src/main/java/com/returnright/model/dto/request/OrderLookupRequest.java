package com.returnright.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Customer identifies an order by its number plus the email used to place it. */
public record OrderLookupRequest(@NotBlank String orderNumber, @NotBlank @Email String email) {}
