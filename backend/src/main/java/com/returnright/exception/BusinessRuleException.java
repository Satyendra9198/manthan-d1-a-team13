package com.returnright.exception;

import java.util.List;

/** A well-formed request that breaks a business rule, e.g. no item in the return is eligible. */
public class BusinessRuleException extends RuntimeException {

  private final String code;
  private final List<String> details;

  public BusinessRuleException(String code, String message) {
    this(code, message, List.of());
  }

  public BusinessRuleException(String code, String message, List<String> details) {
    super(message);
    this.code = code;
    this.details = List.copyOf(details);
  }

  public String getCode() {
    return code;
  }

  public List<String> getDetails() {
    return details;
  }
}
