package com.returnright.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a reviewer declined: kept for the audit trail. */
public record ReviewNoteRequest(@NotBlank @Size(max = 500) String note) {}
