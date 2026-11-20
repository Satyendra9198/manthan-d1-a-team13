package com.returnright.exception;

import java.util.List;

/**
 * One or more selected items cannot be returned as chosen. Every problem is collected so the
 * customer can fix them all at once.
 */
public class ItemSelectionRejectedException extends RuntimeException {

  private final List<String> problems;

  public ItemSelectionRejectedException(List<String> problems) {
    super("Some of the selected items can't be returned.");
    this.problems = List.copyOf(problems);
  }

  /** One entry per problem, formatted {@code <itemId>: <customer-facing reason>}. */
  public List<String> getProblems() {
    return problems;
  }
}
