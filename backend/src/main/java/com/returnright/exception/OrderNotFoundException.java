package com.returnright.exception;

/**
 * No order matches the number and email. A wrong email and an unknown number raise the same error,
 * so the API never reveals that an order number exists.
 */
public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException() {
    super(
        "We couldn't find that order. Check the order number and use the same email as the order.");
  }
}
