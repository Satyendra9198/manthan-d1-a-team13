package com.returnright.auth;

/** Actors allowed to drive the system. {@code SYSTEM} is used for automated transitions. */
public enum Role {
  CUSTOMER,
  WAREHOUSE_AGENT,
  FINANCE_REVIEWER,
  OPERATIONS_MANAGER,
  SYSTEM
}
