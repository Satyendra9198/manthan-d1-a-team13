package com.returnright.exception;

/** The requested resource does not exist or the caller may not see it. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
