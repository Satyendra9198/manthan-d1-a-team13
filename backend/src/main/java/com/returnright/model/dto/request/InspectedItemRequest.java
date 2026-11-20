package com.returnright.model.dto.request;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.RejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The warehouse verdict for one returned line.
 *
 * @param grade condition grade (keys 1–3 on the inspection screen); required when any unit is
 *     accepted
 * @param acceptedQuantity units accepted; fewer than returned is a partial accept (A-05)
 * @param rejectionReason why some units weren't accepted; required for a partial accept
 * @param note internal note; required when the reason is {@code OTHER}
 * @param refundAmountPaise "Partially accept" by amount: refund this much instead of the graded
 *     amount; at most the graded refund of the accepted units, and needs a {@code rejectionReason}
 */
public record InspectedItemRequest(
    @NotNull UUID returnItemId,
    InspectionGrade grade,
    @NotNull @PositiveOrZero Integer acceptedQuantity,
    RejectionReason rejectionReason,
    @Size(max = 500) String note,
    @Positive Long refundAmountPaise) {}
