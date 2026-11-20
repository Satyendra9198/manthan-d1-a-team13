package com.returnright.returns;

/** Internal lifecycle states of a return (assumption A-03: status lives on the return). */
public enum ReturnStatus {
  REQUESTED,
  APPROVED,
  IN_TRANSIT,
  RECEIVED,
  INSPECTED,
  AWAITING_FINANCE_APPROVAL,
  REFUND_PENDING,
  REFUNDED,
  REJECTED,
  CANCELLED
}
