package com.returnright.refunds;

/** No response from the gateway in time. The outcome is unknown, not failed. */
public class GatewayTimeoutException extends RuntimeException {

  public GatewayTimeoutException(String message) {
    super(message);
  }
}
