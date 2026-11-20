package com.returnright.model.dto.response;

import com.returnright.model.enums.EligibilityReason;
import java.time.LocalDate;

public record EligibilityResponse(
    boolean eligible, EligibilityReason reason, String message, LocalDate lastReturnDate) {}
