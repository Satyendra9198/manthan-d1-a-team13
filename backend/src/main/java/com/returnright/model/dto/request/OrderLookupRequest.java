package com.returnright.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Start a return" form: the customer proves they own the order with its number and email.
 *
 * @param orderNumber as printed on the confirmation email, e.g. {@code RR-10482}; case-insensitive
 * @param email the email the order was placed with; case-insensitive
 */
public record OrderLookupRequest(
    @NotBlank @Size(max = 32) String orderNumber, @NotBlank @Email @Size(max = 254) String email) {}
