package com.returnright.model.enums;

import java.util.OptionalInt;

/** The six product categories (assumption A-18) and the return window each one allows. */
public enum ProductCategory {
  ELECTRONICS("Electronics", 10),
  APPAREL("Apparel", 30),
  HOME_KITCHEN("Home & kitchen", 30),
  BEAUTY("Beauty", 30),
  INNERWEAR("Innerwear", null),
  PERISHABLES("Perishables", null);

  private final String displayName;
  private final Integer returnWindowDays;

  ProductCategory(String displayName, Integer returnWindowDays) {
    this.displayName = displayName;
    this.returnWindowDays = returnWindowDays;
  }

  public String getDisplayName() {
    return displayName;
  }

  /** Days after delivery a return is accepted, or empty when the category is non-returnable. */
  public OptionalInt getReturnWindowDays() {
    return returnWindowDays == null ? OptionalInt.empty() : OptionalInt.of(returnWindowDays);
  }

  public boolean isReturnable() {
    return returnWindowDays != null;
  }
}
