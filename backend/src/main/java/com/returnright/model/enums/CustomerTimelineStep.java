package com.returnright.model.enums;

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
