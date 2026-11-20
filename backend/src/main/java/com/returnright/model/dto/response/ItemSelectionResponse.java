package com.returnright.model.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A valid selection, for the "2 items selected · ₹3,148" footer and the next wizard steps.
 *
 * @param selectedItemCount number of distinct lines selected
 * @param selectedUnitCount total units across the selected lines
 * @param estimatedRefundPaise sum of line totals; the final amount depends on inspection
 */
public record ItemSelectionResponse(
    UUID orderId,
    String orderNumber,
    int selectedItemCount,
    int selectedUnitCount,
    long estimatedRefundPaise,
    List<SelectedItemResponse> items) {}
