package com.returnright.model.enums;

/**
 * Why the customer is returning an item (wizard step 3). Reasons that claim a fault need photo
 * evidence (assumption A-19).
 */
public enum ReturnReason {
  TOO_BIG("Too big", false),
  TOO_SMALL("Too small", false),
  DAMAGED_ON_ARRIVAL("Damaged on arrival", true),
  NOT_AS_DESCRIBED("Not as described", true),
  CHANGED_MY_MIND("Changed my mind", false);

  private final String displayName;
  private final boolean photoRequired;

  ReturnReason(String displayName, boolean photoRequired) {
    this.displayName = displayName;
    this.photoRequired = photoRequired;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isPhotoRequired() {
    return photoRequired;
  }
}
