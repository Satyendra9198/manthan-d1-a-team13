package com.returnright.model.dto.response;

import com.returnright.model.enums.RejectionReason;

/**
 * "We couldn't accept this return".
 *
 * @param message customer-facing explanation of the reason
 * @param note the team's note; only shown when the reason is {@code OTHER}
 */
public record RejectionResponse(RejectionReason reason, String message, String note) {}
