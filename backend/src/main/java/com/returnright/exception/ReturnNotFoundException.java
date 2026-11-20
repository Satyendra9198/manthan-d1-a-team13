package com.returnright.exception;

/**
 * No return matches. For customers, an unknown RMA and a wrong email raise the same message, so the
 * API never reveals that a return number exists.
 */
public class ReturnNotFoundException extends RuntimeException {

  private ReturnNotFoundException(String message) {
    super(message);
  }

  public static ReturnNotFoundException forCustomer() {
    return new ReturnNotFoundException(
        "We couldn't find that return. Check the return number and use the email from your"
            + " order.");
  }

  public static ReturnNotFoundException forStaff(String rmaNumber) {
    return new ReturnNotFoundException("No return with number " + rmaNumber + ".");
  }
}
