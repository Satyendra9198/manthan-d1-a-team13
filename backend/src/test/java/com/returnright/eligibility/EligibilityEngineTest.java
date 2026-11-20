package com.returnright.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.returnright.eligibility.EligibilityEngine.ReasonCode;
import com.returnright.eligibility.EligibilityEngine.Result;
import com.returnright.eligibility.EligibilityEngine.UnitFacts;
import com.returnright.eligibility.EligibilityEngine.UnitState;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Part 2 §9: pure engine, "now" injected. Boundaries per [A-01][A-02]. */
class EligibilityEngineTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Instant for a wall-clock time in IST. */
    private static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(IST).toInstant();
    }

    private static UnitFacts apparel(Instant deliveredAt) {
        return unit("DELIVERED", deliveredAt, "APPAREL", "Apparel", true, 30, UnitState.AVAILABLE);
    }

    private static UnitFacts electronics(Instant deliveredAt) {
        return unit("DELIVERED", deliveredAt, "ELECTRONICS", "Electronics", true, 10, UnitState.AVAILABLE);
    }

    private static UnitFacts unit(String orderStatus, Instant deliveredAt, String code, String name,
                                  boolean returnable, Integer windowDays, UnitState state) {
        return new UnitFacts("RR-TEST-1", orderStatus, deliveredAt, code, name, returnable, windowDays, state,
                state == UnitState.IN_ACTIVE_RETURN ? "RMA-26-000042" : null);
    }

    private static Result eval(UnitFacts u, Instant now) {
        return EligibilityEngine.evaluate(u, now, IST);
    }

    @Nested
    class ReturnWindow {

        @ParameterizedTest(name = "apparel delivered {0}, now {1} -> {2}")
        @CsvSource({
            // day 0 = delivery day; day 30 inclusive, day 31 rejected [A-02]
            "2026-10-21T10:00, 2026-11-20T10:00, ELIGIBLE",               // day 30
            "2026-10-21T10:00, 2026-11-20T23:59:59, ELIGIBLE",            // day 30, last second
            "2026-10-21T10:00, 2026-11-21T00:00, RETURN_WINDOW_EXPIRED",  // day 31, first second
            "2026-10-20T10:00, 2026-11-20T10:00, RETURN_WINDOW_EXPIRED",  // day 31
            "2026-11-20T09:00, 2026-11-20T10:00, ELIGIBLE",               // day 0
        })
        void standardThirtyDayWindow(String delivered, String now, ReasonCode expected) {
            assertThat(eval(apparel(ist(delivered)), ist(now)).reasonCode()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "electronics delivered {0}, now {1} -> {2}")
        @CsvSource({
            "2026-11-10T10:00, 2026-11-20T10:00, ELIGIBLE",               // day 10
            "2026-11-10T10:00, 2026-11-20T23:59:59, ELIGIBLE",
            "2026-11-10T10:00, 2026-11-21T00:00, RETURN_WINDOW_EXPIRED",  // day 11
            "2026-11-09T10:00, 2026-11-20T10:00, RETURN_WINDOW_EXPIRED",
        })
        void electronicsTenDayWindow(String delivered, String now, ReasonCode expected) {
            assertThat(eval(electronics(ist(delivered)), ist(now)).reasonCode()).isEqualTo(expected);
        }

        @Test
        void daysAreCountedInIstNotUtc() {
            // Delivered 23:30 IST on 21 Oct = 18:00 UTC the same day; now 00:30 IST on 21 Nov = 19:00 UTC on 20 Nov.
            // In UTC this is still day 30; in IST it is day 31, which must be rejected.
            Instant delivered = ist("2026-10-21T23:30");
            Instant now = ist("2026-11-21T00:30");
            assertThat(now.toString()).startsWith("2026-11-20T19:00");

            Result r = eval(apparel(delivered), now);

            assertThat(r.reasonCode()).isEqualTo(ReasonCode.RETURN_WINDOW_EXPIRED);
            assertThat(r.details()).containsEntry("days_since_delivery", 31L).containsEntry("timezone", "Asia/Kolkata");
        }

        @Test
        void expiredMessageNamesTheWindowAndDates() {
            Result r = eval(apparel(ist("2026-10-20T10:00")), ist("2026-11-20T10:00"));
            assertThat(r.message())
                    .contains("30 days")
                    .contains("20 Oct 2026")
                    .contains("19 Nov 2026");
            assertThat(r.eligible()).isFalse();
        }

        @Test
        void eligibleMessageShowsLastDayAndUrgencyNearTheEnd() {
            assertThat(eval(apparel(ist("2026-11-10T10:00")), ist("2026-11-20T10:00")).message())
                    .isEqualTo("You can return this item until 10 Dec 2026.");
            assertThat(eval(apparel(ist("2026-10-21T10:00")), ist("2026-11-20T10:00")).message())
                    .isEqualTo("Last 1 day to return this item (until 20 Nov 2026).");
            assertThat(eval(apparel(ist("2026-10-23T10:00")), ist("2026-11-20T10:00")).message())
                    .startsWith("Last 3 days");
        }
    }

    @Nested
    class RuleOrder {

        private final Instant now = ist("2026-11-20T10:00");
        private final Instant recent = ist("2026-11-15T10:00");

        @Test
        void cancelledOrderWinsOverEverything() {
            UnitFacts u = unit("CANCELLED", null, "INNERWEAR", "Innerwear", false, null, UnitState.ALREADY_RETURNED);
            assertThat(eval(u, now).reasonCode()).isEqualTo(ReasonCode.ORDER_CANCELLED);
        }

        @Test
        void undeliveredOrderIsNotReturnable() {
            assertThat(eval(unit("SHIPPED", null, "APPAREL", "Apparel", true, 30, UnitState.AVAILABLE), now)
                    .reasonCode()).isEqualTo(ReasonCode.ORDER_NOT_DELIVERED);
            // DELIVERED status without a delivery date is treated as not delivered [A-01]
            assertThat(eval(unit("DELIVERED", null, "APPAREL", "Apparel", true, 30, UnitState.AVAILABLE), now)
                    .reasonCode()).isEqualTo(ReasonCode.ORDER_NOT_DELIVERED);
        }

        @Test
        void deliveryInTheFutureIsInvalidData() {
            Result r = eval(apparel(ist("2026-11-22T10:00")), now);
            assertThat(r.reasonCode()).isEqualTo(ReasonCode.DELIVERY_DATE_INVALID);
            assertThat(r.message()).contains("RR-TEST-1");
        }

        @Test
        void nonReturnableCategoryBeatsUnitState() {
            UnitFacts u = unit("DELIVERED", recent, "PERISHABLES", "Perishables", false, null, UnitState.ALREADY_RETURNED);
            Result r = eval(u, now);
            assertThat(r.reasonCode()).isEqualTo(ReasonCode.CATEGORY_NOT_RETURNABLE);
            assertThat(r.message()).startsWith("Perishables can't be returned");
        }

        @Test
        void unitStatesAreCheckedBeforeTheWindow() {
            Instant old = ist("2026-09-01T10:00");
            assertThat(eval(unit("DELIVERED", old, "APPAREL", "Apparel", true, 30, UnitState.ALREADY_RETURNED), now)
                    .reasonCode()).isEqualTo(ReasonCode.UNIT_ALREADY_RETURNED);
            Result inProgress = eval(unit("DELIVERED", old, "APPAREL", "Apparel", true, 30, UnitState.IN_ACTIVE_RETURN), now);
            assertThat(inProgress.reasonCode()).isEqualTo(ReasonCode.UNIT_RETURN_IN_PROGRESS);
            assertThat(inProgress.message()).contains("RMA-26-000042");
        }
    }

    @Test
    void resultCarriesRuleVersionAndDetails() {
        Result r = eval(apparel(ist("2026-11-15T10:00")), ist("2026-11-20T10:00"));
        assertThat(r.eligible()).isTrue();
        assertThat(r.ruleVersion()).isEqualTo(EligibilityEngine.RULE_VERSION);
        assertThat(r.details())
                .containsEntry("delivered_date", "2026-11-15")
                .containsEntry("evaluated_date", "2026-11-20")
                .containsEntry("last_eligible_date", "2026-12-15")
                .containsEntry("days_remaining", 25L);
    }

    @Test
    void sameInputsGiveSameOutput() {
        UnitFacts u = apparel(ist("2026-11-01T10:00"));
        Instant now = ist("2026-11-20T10:00");
        assertThat(eval(u, now)).isEqualTo(eval(u, now));
    }

    @Test
    void nowAndTimezoneAreRequired() {
        UnitFacts u = apparel(ist("2026-11-01T10:00"));
        assertThatThrownBy(() -> EligibilityEngine.evaluate(u, null, IST)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EligibilityEngine.evaluate(u, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
