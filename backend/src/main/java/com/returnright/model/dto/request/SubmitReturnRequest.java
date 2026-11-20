package com.returnright.model.dto.request;

import com.returnright.model.enums.RefundMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The {@code return} JSON part of the "Submit return" request. Photos travel as separate file parts
 * named {@code photos.<itemId>}.
 *
 * @param detailsConfirmed the "The details and photos are accurate" checkbox
 */
public record SubmitReturnRequest(
    @NotBlank @Size(max = 32) String orderNumber,
    @NotBlank @Email @Size(max = 254) String email,
    @NotEmpty @Size(max = 50) List<@Valid @NotNull ReturnItemRequest> items,
    @NotNull RefundMethod refundMethod,
    @AssertTrue(message = "must confirm the details and photos are accurate")
        boolean detailsConfirmed) {}
