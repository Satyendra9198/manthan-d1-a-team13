package com.returnright.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason recorded in the audit log when staff reject a return. */
public record DecisionRequest(@NotBlank @Size(max = 500) String reason) {}
