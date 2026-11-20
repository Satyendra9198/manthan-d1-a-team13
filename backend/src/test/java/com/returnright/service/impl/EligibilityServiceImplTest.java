package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.model.enums.EligibilityReason;
import com.returnright.model.enums.ProductCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EligibilityServiceImplTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  /** 20 Nov 2026, 12:00 IST. */
  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private static final LocalDate TODAY_IST = LocalDate.of(2026, 11, 20);

  private final EligibilityServiceImpl engine =
      new EligibilityServiceImpl(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void apparelReturnedOnDay30IsEligible() {
    EligibilityResult result = engine.evaluate(item(ProductCategory.APPAREL, deliveredDaysAgo(30)));

    assertThat(result.eligible()).isTrue();
    assertThat(result.reason()).isEqualTo(EligibilityReason.ELIGIBLE);
  }

  @Test
  void apparelReturnedOnDay31IsRejectedAsWindowExpired() {
    EligibilityResult result = engine.evaluate(item(ProductCategory.APPAREL, deliveredDaysAgo(31)));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.RETURN_WINDOW_EXPIRED);
  }

  @Test
  void electronicsReturnedOnDay10IsEligible() {
    EligibilityResult result =
        engine.evaluate(item(ProductCategory.ELECTRONICS, deliveredDaysAgo(10)));

    assertThat(result.eligible()).isTrue();
  }

  @Test
  void electronicsReturnedOnDay11IsRejectedAsWindowExpired() {
    EligibilityResult result =
        engine.evaluate(item(ProductCategory.ELECTRONICS, deliveredDaysAgo(11)));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.RETURN_WINDOW_EXPIRED);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProductCategory.class,
      names = {"APPAREL", "HOME_KITCHEN", "BEAUTY"})
  void standardCategoriesUseThe30DayWindow(ProductCategory category) {
    assertThat(engine.evaluate(item(category, deliveredDaysAgo(30))).eligible()).isTrue();
    assertThat(engine.evaluate(item(category, deliveredDaysAgo(31))).eligible()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProductCategory.class,
      names = {"INNERWEAR", "PERISHABLES"})
  void nonReturnableCategoryDeliveredTodayIsRejected(ProductCategory category) {
    EligibilityResult result = engine.evaluate(item(category, deliveredDaysAgo(0)));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.NON_RETURNABLE_CATEGORY);
    assertThat(result.lastReturnDate()).isNull();
  }

  @Test
  void nonReturnableCategoryIsReportedBeforeWindowExpiry() {
    EligibilityResult result =
        engine.evaluate(item(ProductCategory.INNERWEAR, deliveredDaysAgo(45)));

    assertThat(result.reason()).isEqualTo(EligibilityReason.NON_RETURNABLE_CATEGORY);
  }

  @Test
  void undeliveredItemIsRejectedAsNotDelivered() {
    EligibilityResult result = engine.evaluate(item(ProductCategory.APPAREL, null));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.NOT_DELIVERED);
  }

  @Test
  void windowIsCountedInIndianDatesNotUtcDates() {
    // 21 Oct 00:30 IST is still 20 Oct in UTC. In IST it is day 30 (allowed); in UTC it would be
    // day 31.
    Instant deliveredAt = ZonedDateTime.of(2026, 10, 21, 0, 30, 0, 0, IST).toInstant();

    EligibilityResult result = engine.evaluate(item(ProductCategory.APPAREL, deliveredAt));

    assertThat(result.eligible()).isTrue();
  }

  @Test
  void windowClosesAtIndianMidnightNotUtcMidnight() {
    // 21 Nov 00:01 IST is still 20 Nov in UTC; in IST, a 21 Oct delivery is now on day 31.
    Instant justAfterIstMidnight = ZonedDateTime.of(2026, 11, 21, 0, 1, 0, 0, IST).toInstant();
    EligibilityServiceImpl lateEngine =
        new EligibilityServiceImpl(Clock.fixed(justAfterIstMidnight, ZoneOffset.UTC));
    Instant deliveredAt = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, IST).toInstant();

    EligibilityResult result = lateEngine.evaluate(item(ProductCategory.APPAREL, deliveredAt));

    assertThat(result.reason()).isEqualTo(EligibilityReason.RETURN_WINDOW_EXPIRED);
  }

  @Test
  void zeroRequestedQuantityIsRejectedAsInvalid() {
    EligibilityResult result =
        engine.evaluate(
            new ItemEligibilityInput(ProductCategory.APPAREL, deliveredDaysAgo(1), 2, 0, 0));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.INVALID_QUANTITY);
  }

  @Test
  void requestingMoreThanTheRemainingQuantityIsRejected() {
    EligibilityResult result =
        engine.evaluate(
            new ItemEligibilityInput(ProductCategory.APPAREL, deliveredDaysAgo(1), 3, 1, 3));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.QUANTITY_EXCEEDS_REMAINING);
  }

  @Test
  void requestingTheExactRemainingQuantityIsEligible() {
    EligibilityResult result =
        engine.evaluate(
            new ItemEligibilityInput(ProductCategory.APPAREL, deliveredDaysAgo(1), 3, 1, 2));

    assertThat(result.eligible()).isTrue();
  }

  @Test
  void fullyReturnedLineIsRejectedAsAlreadyReturned() {
    EligibilityResult result =
        engine.evaluate(
            new ItemEligibilityInput(ProductCategory.APPAREL, deliveredDaysAgo(1), 2, 2, 1));

    assertThat(result.eligible()).isFalse();
    assertThat(result.reason()).isEqualTo(EligibilityReason.ALREADY_RETURNED);
  }

  @Test
  void eligibleResultReportsTheLastReturnDate() {
    EligibilityResult result =
        engine.evaluate(item(ProductCategory.ELECTRONICS, deliveredDaysAgo(3)));

    assertThat(result.lastReturnDate()).isEqualTo(TODAY_IST.minusDays(3).plusDays(10));
  }

  @Test
  void expiredResultMessageTellsTheCustomerWhenTheWindowClosed() {
    EligibilityResult result = engine.evaluate(item(ProductCategory.APPAREL, deliveredDaysAgo(31)));

    assertThat(result.lastReturnDate()).isEqualTo(LocalDate.of(2026, 11, 19));
    assertThat(result.message()).contains("19 Nov 2026");
  }

  @Test
  void everyOutcomeCarriesACustomerFacingMessage() {
    assertThat(engine.evaluate(item(ProductCategory.APPAREL, deliveredDaysAgo(1))).message())
        .isNotBlank();
    assertThat(engine.evaluate(item(ProductCategory.INNERWEAR, deliveredDaysAgo(1))).message())
        .isNotBlank();
    assertThat(engine.evaluate(item(ProductCategory.APPAREL, null)).message()).isNotBlank();
  }

  private static ItemEligibilityInput item(ProductCategory category, Instant deliveredAt) {
    return new ItemEligibilityInput(category, deliveredAt, 1, 0, 1);
  }

  /** Delivered at 10:00 IST, {@code days} calendar days before today (IST). */
  private static Instant deliveredDaysAgo(int days) {
    return TODAY_IST.minusDays(days).atTime(LocalTime.of(10, 0)).atZone(IST).toInstant();
  }
}
