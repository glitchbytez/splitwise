package com.glitchbytez.splitwise.expense.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BasisPointsTest {

    @ParameterizedTest
    @CsvSource({"50.00, 5000", "33.33, 3333", "0, 0", "100, 10000", "12.5, 1250", "0.01, 1"})
    void convertsPercentagesToBasisPoints(String percentage, int expected) {
        assertThat(BasisPoints.fromPercentage(new BigDecimal(percentage))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"5000, 50.00", "3333, 33.33", "0, 0.00", "10000, 100.00", "1, 0.01"})
    void formatsBasisPointsAsPercentages(int bp, String expected) {
        assertThat(BasisPoints.toPercentage(bp)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"33.333", "0.001"})
    void rejectsMoreThanTwoDecimals(String percentage) {
        assertThatThrownBy(() -> BasisPoints.fromPercentage(new BigDecimal(percentage)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Percentages can have at most two decimal places");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "100.01", "250"})
    void rejectsPercentagesOutsideZeroToOneHundred(String percentage) {
        assertThatThrownBy(() -> BasisPoints.fromPercentage(new BigDecimal(percentage)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Percentages must be between 0 and 100");
    }
}
