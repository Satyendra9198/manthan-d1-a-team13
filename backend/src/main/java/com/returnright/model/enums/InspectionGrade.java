package com.returnright.model.enums;

/** Condition grade assigned by the warehouse, with the share of the price it refunds (A-06). */
public enum InspectionGrade {
  A(100, "Like new"),
  B(100, "Good, minor signs of handling"),
  C(50, "Visible wear or damage");

  private final int refundPercent;
  private final String description;

  InspectionGrade(int refundPercent, String description) {
    this.refundPercent = refundPercent;
    this.description = description;
  }

  public int getRefundPercent() {
    return refundPercent;
  }

  public String getDescription() {
    return description;
  }
}
