package com.returnright.returns;

/** Statuses shown to the customer on the return timeline. */
public enum CustomerTimelineStep {
  REQUESTED,
  APPROVED,
  IN_TRANSIT,
  RECEIVED,
  INSPECTED,
  REFUNDED,
  REJECTED,
  CANCELLED
}
