package com.returnright.model.dto.response;

import java.time.LocalDate;

/**
 * "Your return label": the frontend draws the QR code from {@code qrCodeValue}.
 *
 * @param dropOffBy last day (Asia/Kolkata) to hand in the parcel; the label expires after it (A-13)
 */
public record ReturnLabelResponse(
    String qrCodeValue, LocalDate dropOffBy, String shipTo, String instructions) {}
