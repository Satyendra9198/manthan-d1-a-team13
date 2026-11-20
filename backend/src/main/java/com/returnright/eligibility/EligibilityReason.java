package com.returnright.eligibility;

/** Machine-readable outcome of an eligibility check. */
public enum EligibilityReason {
  ELIGIBLE,
  NON_RETURNABLE_CATEGORY,
  NOT_DELIVERED,
  INVALID_QUANTITY,
  ALREADY_RETURNED,
  QUANTITY_EXCEEDS_REMAINING,
  RETURN_WINDOW_EXPIRED
}
