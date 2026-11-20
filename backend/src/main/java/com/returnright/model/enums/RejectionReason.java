package com.returnright.model.enums;

/**
 * Why a return, or part of it, was not accepted. The customer sees {@link #getCustomerMessage()}.
 * Only reasons marked {@code selectableByWarehouse} appear in the warehouse reject and
 * partial-accept dialogs; the others are set by the finance and operations reviews.
 */
public enum RejectionReason {
  NOT_AS_DESCRIBED("The item we received doesn't match the description in your request.", true),
  USED_OR_WORN("The item shows signs of use or wear.", true),
  MISSING_PARTS_OR_TAGS("Parts, accessories or tags are missing.", true),
  OUTSIDE_RETURN_WINDOW("The item arrived after its return window had closed.", true),
  OTHER("See the note from our team.", true),
  REQUEST_DECLINED("Your return request was not approved after review.", false),
  REFUND_DECLINED("The refund was not approved after review.", false);

  private final String customerMessage;
  private final boolean selectableByWarehouse;

  RejectionReason(String customerMessage, boolean selectableByWarehouse) {
    this.customerMessage = customerMessage;
    this.selectableByWarehouse = selectableByWarehouse;
  }

  public String getCustomerMessage() {
    return customerMessage;
  }

  public boolean isSelectableByWarehouse() {
    return selectableByWarehouse;
  }

  /** {@code OTHER} only makes sense with a note explaining it. */
  public boolean isNoteRequired() {
    return this == OTHER;
  }
}
