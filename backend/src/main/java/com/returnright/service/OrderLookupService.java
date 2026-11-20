package com.returnright.service;

import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.OrderLookupResponse;

/** Finds a customer's order so they can start a return. */
public interface OrderLookupService {

  /**
   * Returns the order with each line's return eligibility.
   *
   * @throws com.returnright.exception.OrderNotFoundException if the number is unknown or the email
   *     does not match
   */
  OrderLookupResponse findOrder(OrderLookupRequest request);
}
