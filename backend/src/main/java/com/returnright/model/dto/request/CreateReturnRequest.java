package com.returnright.model.dto.request;

import com.returnright.model.enums.RefundMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateReturnRequest(
    @NotBlank String orderNumber,
    @NotBlank @Email String email,
    @NotNull RefundMethod refundMethod,
    @NotEmpty List<@Valid ReturnItemRequest> items) {}
