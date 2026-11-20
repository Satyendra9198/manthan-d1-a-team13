package com.returnright.model.enums;

/** Events that move a return between states. */
public enum ReturnEvent {
  APPROVE,
  REJECT,
  CANCEL,
  MARK_IN_TRANSIT,
  RECEIVE,
  COMPLETE_INSPECTION,
  REJECT_AFTER_INSPECTION,
  SEND_TO_FINANCE,
  START_REFUND,
  FINANCE_APPROVE,
  FINANCE_REJECT,
  CONFIRM_REFUND
}
