package com.returnright.service;

import java.time.Instant;
import java.util.List;

/** Flags customers with frequent returns for review (assumptions A-10, A-11). */
public interface CustomerFlagService {

  /**
   * Returns true when the customer has more than 3 requests in the rolling 30 days up to now.
   *
   * @param requestTimes creation times of the customer's non-cancelled return requests, including
   *     the one being submitted
   */
  boolean isFlagged(List<Instant> requestTimes);
}
