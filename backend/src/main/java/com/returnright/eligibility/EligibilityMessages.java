package com.returnright.eligibility;

import com.returnright.eligibility.EligibilityEngine.ReasonCode;
import com.returnright.eligibility.EligibilityEngine.UnitFacts;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The ONLY source of customer-facing eligibility text (Part 2 §9.4). */
public final class EligibilityMessages {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private EligibilityMessages() {}

    static String render(ReasonCode code, UnitFacts u, LocalDate delivered, LocalDate lastEligible, Long daysRemaining) {
        return switch (code) {
            case ELIGIBLE -> daysRemaining != null && daysRemaining <= 2
                    ? "Last %d day%s to return this item (until %s).".formatted(daysRemaining + 1,
                            daysRemaining == 0 ? "" : "s", D.format(lastEligible))
                    : "You can return this item until %s.".formatted(D.format(lastEligible));
            case ORDER_CANCELLED ->
                    "This order was cancelled, so there's nothing to return. If you were charged, any refund is handled automatically.";
            case ORDER_NOT_DELIVERED ->
                    "This order hasn't been delivered yet. You'll be able to start a return once it arrives.";
            case DELIVERY_DATE_INVALID ->
                    "We can't check this item right now. Please contact support and quote order %s.".formatted(u.orderNumber());
            case CATEGORY_NOT_RETURNABLE ->
                    "%s can't be returned for hygiene and safety reasons. If the item arrived damaged or wrong, please contact support."
                            .formatted(u.categoryName());
            case UNIT_ALREADY_RETURNED -> "This item has already been returned.";
            case UNIT_RETURN_IN_PROGRESS -> u.activeRmaNumber() == null
                    ? "A return for this item is already in progress."
                    : "A return for this item is already in progress (%s).".formatted(u.activeRmaNumber());
            case RETURN_WINDOW_EXPIRED ->
                    "The return window for %s is %d days from delivery. This item was delivered on %s, so the last day to return it was %s."
                            .formatted(u.categoryName(), u.windowDays(), D.format(delivered), D.format(lastEligible));
        };
    }
}
