package com.returnright.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RefundCalculatorTest {

  private final RefundCalculator calculator = new RefundCalculator();

  @Test
  void gradeAItemIsRefundedInFull() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(250_000, 1, 1, InspectionGrade.A)))
        .isEqualTo(250_000);
  }

  @Test
  void gradeBItemIsRefundedInFull() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(250_000, 2, 2, InspectionGrade.B)))
        .isEqualTo(500_000);
  }

  @Test
  void gradeCItemIsRefundedAtHalfPrice() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(250_000, 1, 1, InspectionGrade.C)))
        .isEqualTo(125_000);
  }

  @Test
  void gradeCHalfPaisaIsRoundedDown() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(99_999, 1, 1, InspectionGrade.C)))
        .isEqualTo(49_999);
  }

  @Test
  void partialAcceptRefundsOnlyTheAcceptedUnits() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(100_000, 3, 2, InspectionGrade.A)))
        .isEqualTo(200_000);
  }

  @Test
  void partialAcceptOfGradeCItemsRefundsHalfOfTheAcceptedUnits() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(100_000, 3, 2, InspectionGrade.C)))
        .isEqualTo(100_000);
  }

  @Test
  void rejectedLineIsRefundedNothing() {
    assertThat(calculator.lineRefundPaise(new InspectedLine(100_000, 2, 0, InspectionGrade.C)))
        .isZero();
  }

  @Test
  void returnTotalIsTheSumOfGradeAdjustedLines() {
    List<InspectedLine> lines =
        List.of(
            new InspectedLine(100_000, 1, 1, InspectionGrade.A),
            new InspectedLine(200_000, 1, 1, InspectionGrade.C),
            new InspectedLine(300_000, 1, 0, InspectionGrade.B));

    assertThat(calculator.totalRefundPaise(lines)).isEqualTo(200_000);
  }

  @Test
  void acceptingMoreUnitsThanReturnedIsRefused() {
    assertThatThrownBy(
            () -> calculator.lineRefundPaise(new InspectedLine(100_000, 1, 2, InspectionGrade.A)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeAcceptedQuantityIsRefused() {
    assertThatThrownBy(
            () -> calculator.lineRefundPaise(new InspectedLine(100_000, 1, -1, InspectionGrade.A)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
