package com.returnright.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Staff sign-in form. */
public record LoginRequest(
    @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}
