package com.returnright.service.impl;

import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.model.enums.EligibilityReason;
import com.returnright.model.enums.ProductCategory;
import com.returnright.service.EligibilityService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Pure, deterministic eligibility rules. "Now" comes from the injected clock.
 *
 * <p>Checks run in a fixed order so the customer sees the most fundamental reason first: category,
 * delivery, quantity, then the return window.
 */
@Service
public class EligibilityServiceImpl implements EligibilityService {

  public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");

  private static final DateTimeFormatter CUSTOMER_DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final Clock clock;

  public EligibilityServiceImpl(Clock clock) {
    this.clock = clock;
  }

  @Override
  public EligibilityResult evaluate(ItemEligibilityInput input) {
    ProductCategory category = input.category();
    if (!category.isReturnable()) {
      return ineligible(
          EligibilityReason.NON_RETURNABLE_CATEGORY,
          category.getDisplayName() + " can't be returned for hygiene and safety reasons.",
          null);
    }
    if (input.deliveredAt() == null) {
      return ineligible(
          EligibilityReason.NOT_DELIVERED,
          "This item hasn't been delivered yet, so it can't be returned.",
          null);
    }

    int remainingQuantity = input.quantityOrdered() - input.quantityAlreadyReturned();
    if (remainingQuantity <= 0) {
      return ineligible(
          EligibilityReason.ALREADY_RETURNED, "This item has already been returned.", null);
    }
    if (input.quantityRequested() <= 0) {
      return ineligible(
          EligibilityReason.INVALID_QUANTITY, "Choose at least one unit to return.", null);
    }
    if (input.quantityRequested() > remainingQuantity) {
      return ineligible(
          EligibilityReason.QUANTITY_EXCEEDS_REMAINING,
          "You can return at most " + remainingQuantity + " of this item.",
          null);
    }

    int windowDays = category.getReturnWindowDays().orElseThrow();
    LocalDate deliveredOn = input.deliveredAt().atZone(STORE_ZONE).toLocalDate();
    LocalDate lastReturnDate = deliveredOn.plusDays(windowDays);
    LocalDate today = LocalDate.now(clock.withZone(STORE_ZONE));

    if (today.isAfter(lastReturnDate)) {
      return ineligible(
          EligibilityReason.RETURN_WINDOW_EXPIRED,
          "The "
              + windowDays
              + "-day return window for "
              + category.getDisplayName()
              + " closed on "
              + CUSTOMER_DATE.format(lastReturnDate)
              + ".",
          lastReturnDate);
    }
    return new EligibilityResult(
        true,
        EligibilityReason.ELIGIBLE,
        "You can return this item until " + CUSTOMER_DATE.format(lastReturnDate) + ".",
        lastReturnDate);
  }

  private static EligibilityResult ineligible(
      EligibilityReason reason, String message, LocalDate lastReturnDate) {
    return new EligibilityResult(false, reason, message, lastReturnDate);
  }
}
