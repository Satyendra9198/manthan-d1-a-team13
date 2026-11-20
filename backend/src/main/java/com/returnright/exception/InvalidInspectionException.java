package com.returnright.exception;

import java.util.List;

/** The inspection result breaks a rule. Every problem is collected and reported together. */
public class InvalidInspectionException extends RuntimeException {

  private final List<String> problems;

  public InvalidInspectionException(List<String> problems) {
    super("The inspection result can't be saved.");
    this.problems = List.copyOf(problems);
  }

  public List<String> getProblems() {
    return problems;
  }
}
