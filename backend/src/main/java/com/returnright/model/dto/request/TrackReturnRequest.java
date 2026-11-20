package com.returnright.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Track my return": the customer proves the return is theirs with the email from the order.
 *
 * @param rmaNumber e.g. {@code RMA-2026-00731}; case-insensitive
 */
public record TrackReturnRequest(
    @NotBlank @Size(max = 32) String rmaNumber, @NotBlank @Email @Size(max = 254) String email) {}
