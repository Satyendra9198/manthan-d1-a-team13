package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RupeesTest {

  @Test
  void wholeRupeesHaveNoDecimals() {
    assertThat(Rupees.format(189_900)).isEqualTo("₹1,899");
  }

  @Test
  void paiseAreShownWithTwoDecimals() {
    assertThat(Rupees.format(94_950)).isEqualTo("₹949.50");
  }

  @Test
  void lakhsUseIndianGrouping() {
    assertThat(Rupees.format(10_000_000)).isEqualTo("₹1,00,000");
  }

  @Test
  void croresUseIndianGrouping() {
    assertThat(Rupees.format(1_234_567_850)).isEqualTo("₹1,23,45,678.50");
  }

  @Test
  void amountsUnderAThousandHaveNoSeparator() {
    assertThat(Rupees.format(99_900)).isEqualTo("₹999");
  }
}
