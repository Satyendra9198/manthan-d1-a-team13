package com.returnright.core;

import com.returnright.core.util.Money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.returnright.core.enums.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Money rules: paise as long; grade C = 50% half-up [A-10][BR-7]. */
class MoneyTest {

    @ParameterizedTest(name = "grade C of {0} paise = {1}")
    @CsvSource({
        "2499900, 1249950", // RR-DEMO-104: 24,999.00 -> 12,499.50
        "149900, 74950",
        "1, 1",             // half-up: 0.5 paisa rounds to 1
        "3, 2",
        "99999, 50000",
    })
    void gradeCIsHalfRoundedUp(long price, long expected) {
        assertThat(Money.gradeRefundPaise(price, Grade.C)).isEqualTo(expected);
    }

    @Test
    void gradesAAndBRefundInFull() {
        assertThat(Money.gradeRefundPaise(149_900, Grade.A)).isEqualTo(149_900);
        assertThat(Money.gradeRefundPaise(149_901, Grade.B)).isEqualTo(149_901);
    }

    @Test
    void priceMustBePositive() {
        assertThatThrownBy(() -> Money.gradeRefundPaise(0, Grade.A)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.gradeRefundPaise(-5, Grade.C)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} paise -> {1}")
    @CsvSource(delimiter = '|', value = {
        "0|₹0.00",
        "5|₹0.05",
        "99900|₹999.00",
        "1000000|₹10,000.00",
        "12345678|₹1,23,456.78",
        "1000000000|₹1,00,00,000.00",
        "-149900|-₹1,499.00",
    })
    void formatsWithIndianGrouping(long paise, String expected) {
        assertThat(Money.formatInr(paise)).isEqualTo(expected);
    }
}
