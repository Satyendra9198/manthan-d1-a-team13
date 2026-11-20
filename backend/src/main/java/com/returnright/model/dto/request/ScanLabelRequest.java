package com.returnright.model.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Label code read from the parcel's QR code. */
public record ScanLabelRequest(@NotBlank String labelCode) {}
