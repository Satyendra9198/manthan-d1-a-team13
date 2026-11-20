package com.returnright.finance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FinanceApprovalPolicyTest {

  private final FinanceApprovalPolicy policy = new FinanceApprovalPolicy();

  @Test
  void refundOfExactly10000RupeesDoesNotNeedFinanceApproval() {
    assertThat(policy.requiresApproval(1_000_000)).isFalse();
  }

  @Test
  void refundOnePaisaAbove10000RupeesNeedsFinanceApproval() {
    assertThat(policy.requiresApproval(1_000_001)).isTrue();
  }

  @Test
  void smallRefundDoesNotNeedFinanceApproval() {
    assertThat(policy.requiresApproval(49_900)).isFalse();
  }
}
