package com.returnright.customers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerFlagPolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private final CustomerFlagPolicy policy =
      new CustomerFlagPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void customerWithThreeRequestsIn30DaysIsNotFlagged() {
    assertThat(policy.isFlagged(List.of(daysAgo(0), daysAgo(5), daysAgo(20)))).isFalse();
  }

  @Test
  void customerWithFourRequestsIn30DaysIsFlagged() {
    assertThat(policy.isFlagged(List.of(daysAgo(0), daysAgo(5), daysAgo(20), daysAgo(29))))
        .isTrue();
  }

  @Test
  void requestsOlderThan30DaysAreNotCounted() {
    assertThat(policy.isFlagged(List.of(daysAgo(0), daysAgo(5), daysAgo(20), daysAgo(31))))
        .isFalse();
  }

  @Test
  void requestExactly30DaysAgoIsCounted() {
    assertThat(policy.isFlagged(List.of(daysAgo(0), daysAgo(5), daysAgo(20), daysAgo(30))))
        .isTrue();
  }

  @Test
  void customerWithNoRequestsIsNotFlagged() {
    assertThat(policy.isFlagged(List.of())).isFalse();
  }

  private static Instant daysAgo(int days) {
    return NOW.minus(Duration.ofDays(days));
  }
}
