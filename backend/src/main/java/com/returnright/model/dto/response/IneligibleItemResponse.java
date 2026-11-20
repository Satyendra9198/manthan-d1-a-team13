package com.returnright.model.dto.response;

import com.returnright.model.enums.EligibilityReason;
import java.util.UUID;

/** An item the customer asked to return that was left out of the return, with the reason. */
public record IneligibleItemResponse(UUID orderItemId, EligibilityReason reason, String message) {}
