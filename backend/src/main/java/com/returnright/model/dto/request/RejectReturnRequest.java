package com.returnright.model.dto.request;

import com.returnright.model.enums.RejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The "Reject return" dialog.
 *
 * @param reason shown to the customer
 * @param note internal; shown to the customer only when the reason is {@code OTHER}, where it is
 *     required
 */
public record RejectReturnRequest(@NotNull RejectionReason reason, @Size(max = 500) String note) {}
