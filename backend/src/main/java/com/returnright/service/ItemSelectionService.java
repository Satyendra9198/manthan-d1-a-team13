package com.returnright.service;

import com.returnright.model.dto.request.ItemSelectionRequest;
import com.returnright.model.dto.response.ItemSelectionResponse;

/** Checks the items a customer picked before they continue to reasons and refund method. */
public interface ItemSelectionService {

  /**
   * Validates the selection against the order and the eligibility rules. Nothing is saved; the
   * return is created when the customer submits the final step.
   *
   * @throws com.returnright.exception.OrderNotFoundException if the order number and email do not
   *     match
   * @throws com.returnright.exception.ItemSelectionRejectedException if any selected item is
   *     unknown, repeated, not returnable, or asked for in a quantity that is not available
   */
  ItemSelectionResponse validateSelection(ItemSelectionRequest request);
}
