package com.returnright.model.domain;

import com.returnright.model.enums.EligibilityReason;
import java.time.LocalDate;

/**
 * Outcome of an eligibility check.
 *
 * @param message customer-facing explanation
 * @param lastReturnDate last calendar day (Asia/Kolkata) a return is accepted, or {@code null} when
 *     no window applies
 */
public record EligibilityResult(
    boolean eligible, EligibilityReason reason, String message, LocalDate lastReturnDate) {}
