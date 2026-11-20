package com.returnright.refunds;

/** Where the refund goes. Store credit never calls the gateway (assumption A-09). */
public enum RefundMethod {
  ORIGINAL_PAYMENT,
  STORE_CREDIT
}
