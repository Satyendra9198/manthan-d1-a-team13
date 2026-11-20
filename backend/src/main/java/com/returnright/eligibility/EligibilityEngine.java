package com.returnright.eligibility;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure, deterministic eligibility rules (Part 2 §9). No I/O, no clock reads: "now" is injected.
 * First failing rule wins, in the documented order R1..R7.
 */
public final class EligibilityEngine {

    public static final String RULE_VERSION = "2026-11-v1";

    public enum ReasonCode {
        ELIGIBLE,
        ORDER_CANCELLED,
        ORDER_NOT_DELIVERED,
        DELIVERY_DATE_INVALID,
        CATEGORY_NOT_RETURNABLE,
        UNIT_ALREADY_RETURNED,
        UNIT_RETURN_IN_PROGRESS,
        RETURN_WINDOW_EXPIRED
    }

    public enum UnitState { AVAILABLE, IN_ACTIVE_RETURN, ALREADY_RETURNED }

    /** Facts about one physical unit, gathered by the caller in one query. */
    public record UnitFacts(
            String orderNumber,
            String orderStatus,
            Instant deliveredAt,
            String categoryCode,
            String categoryName,
            boolean returnable,
            Integer windowDays,
            UnitState unitState,
            String activeRmaNumber) {}

    public record Result(boolean eligible, ReasonCode reasonCode, String message, Map<String, Object> details,
                         String ruleVersion) {}

    private EligibilityEngine() {}

    public static Result evaluate(UnitFacts u, Instant now, ZoneId tz) {
        if (now == null || tz == null) {
            throw new IllegalArgumentException("now and tz are required");
        }
        LocalDate today = now.atZone(tz).toLocalDate();
        LocalDate delivered = u.deliveredAt() == null ? null : u.deliveredAt().atZone(tz).toLocalDate();
        Long daysSince = delivered == null ? null : ChronoUnit.DAYS.between(delivered, today); // [A-02] day 0 = delivery day
        LocalDate lastEligible = delivered == null || u.windowDays() == null ? null : delivered.plusDays(u.windowDays());
        Long daysRemaining = lastEligible == null ? null : ChronoUnit.DAYS.between(today, lastEligible);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("category_code", u.categoryCode());
        details.put("window_days", u.windowDays());
        details.put("delivered_date", delivered == null ? null : delivered.toString());
        details.put("evaluated_date", today.toString());
        details.put("days_since_delivery", daysSince);
        details.put("last_eligible_date", lastEligible == null ? null : lastEligible.toString());
        details.put("days_remaining", daysRemaining);
        details.put("timezone", tz.getId());

        ReasonCode code;
        if ("CANCELLED".equals(u.orderStatus())) {                                    // R1
            code = ReasonCode.ORDER_CANCELLED;
        } else if (!"DELIVERED".equals(u.orderStatus()) || delivered == null) {       // R2 [A-01]
            code = ReasonCode.ORDER_NOT_DELIVERED;
        } else if (delivered.isAfter(today)) {                                       // R3
            code = ReasonCode.DELIVERY_DATE_INVALID;
        } else if (!u.returnable()) {                                                // R4
            code = ReasonCode.CATEGORY_NOT_RETURNABLE;
        } else if (u.unitState() == UnitState.ALREADY_RETURNED) {                    // R5
            code = ReasonCode.UNIT_ALREADY_RETURNED;
        } else if (u.unitState() == UnitState.IN_ACTIVE_RETURN) {                    // R6
            code = ReasonCode.UNIT_RETURN_IN_PROGRESS;
        } else if (daysSince > u.windowDays()) {                                     // R7 day 30 ok, day 31 not
            code = ReasonCode.RETURN_WINDOW_EXPIRED;
        } else {
            code = ReasonCode.ELIGIBLE;
        }
        String message = EligibilityMessages.render(code, u, delivered, lastEligible, daysRemaining);
        return new Result(code == ReasonCode.ELIGIBLE, code, message, details, RULE_VERSION);
    }
}
