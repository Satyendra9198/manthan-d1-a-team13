package com.returnright.exception;

/**
 * Sign-in failed. The same error is used for an unknown email, a wrong password and a deactivated
 * account, so the response never reveals which accounts exist.
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Email or password is incorrect.");
  }
}
