package com.returnright.exception;

import java.util.List;

/** One or more photos break the upload rules. Every problem is collected and reported together. */
public class InvalidPhotosException extends RuntimeException {

  private final List<String> problems;

  public InvalidPhotosException(List<String> problems) {
    super("Some photos can't be accepted.");
    this.problems = List.copyOf(problems);
  }

  /** One entry per problem, formatted {@code <itemId>: <customer-facing reason>}. */
  public List<String> getProblems() {
    return problems;
  }
}
