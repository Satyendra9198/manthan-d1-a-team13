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
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Pure, deterministic eligibility rules. "Now" comes from the injected clock.
 *
 * <p>Rule order: category, delivery, quantity, return window. The first failing rule decides.
 */
@Service
public class EligibilityServiceImpl implements EligibilityService {

  public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");

  private static final Set<ProductCategory> NON_RETURNABLE =
      EnumSet.of(ProductCategory.INNERWEAR, ProductCategory.PERISHABLES);
  private static final int ELECTRONICS_WINDOW_DAYS = 10;
  private static final int STANDARD_WINDOW_DAYS = 30;
  private static final DateTimeFormatter DISPLAY_DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final Clock clock;

  public EligibilityServiceImpl(Clock clock) {
    this.clock = clock;
  }

  @Override
  public EligibilityResult evaluate(ItemEligibilityInput input) {
    if (NON_RETURNABLE.contains(input.category())) {
      return rejected(
          EligibilityReason.NON_RETURNABLE_CATEGORY,
          "Innerwear and perishable items can't be returned for hygiene and safety reasons.");
    }
    if (input.deliveredAt() == null) {
      return rejected(
          EligibilityReason.NOT_DELIVERED,
          "This item hasn't been delivered yet. You can request a return once it arrives.");
    }
    if (input.quantityRequested() < 1) {
      return rejected(EligibilityReason.INVALID_QUANTITY, "Choose at least one unit to return.");
    }
    int remaining = input.quantityOrdered() - input.quantityAlreadyReturned();
    if (remaining <= 0) {
      return rejected(
          EligibilityReason.ALREADY_RETURNED,
          "All units of this item are already part of a return.");
    }
    if (input.quantityRequested() > remaining) {
      return rejected(
          EligibilityReason.QUANTITY_EXCEEDS_REMAINING,
          "You can return up to " + remaining + " unit(s) of this item.");
    }

    int windowDays =
        input.category() == ProductCategory.ELECTRONICS
            ? ELECTRONICS_WINDOW_DAYS
            : STANDARD_WINDOW_DAYS;
    LocalDate deliveredOn = LocalDate.ofInstant(input.deliveredAt(), STORE_ZONE);
    LocalDate lastReturnDate = deliveredOn.plusDays(windowDays);
    LocalDate today = LocalDate.ofInstant(clock.instant(), STORE_ZONE);

    if (today.isAfter(lastReturnDate)) {
      return new EligibilityResult(
          false,
          EligibilityReason.RETURN_WINDOW_EXPIRED,
          "The "
              + windowDays
              + "-day return window for this item closed on "
              + DISPLAY_DATE.format(lastReturnDate)
              + ".",
          lastReturnDate);
    }
    return new EligibilityResult(
        true,
        EligibilityReason.ELIGIBLE,
        "You can return this item until " + DISPLAY_DATE.format(lastReturnDate) + ".",
        lastReturnDate);
  }

  private static EligibilityResult rejected(EligibilityReason reason, String message) {
    return new EligibilityResult(false, reason, message, null);
  }
}
