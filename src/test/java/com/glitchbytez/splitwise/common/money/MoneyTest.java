package com.glitchbytez.splitwise.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Nested
    class Parsing {

        @ParameterizedTest
        @CsvSource({
                "12.50, 1250",
                "12.5, 1250",
                "12, 1200",
                "0, 0",
                "0.01, 1",
                "-3.07, -307",
                "' 7.25 ', 725",
                "92233720368547758.07, 9223372036854775807"
        })
        void parsesDecimalStringsIntoMinorUnits(String input, long expectedMinor) {
            Money money = Money.parse(input);

            assertThat(money.amountMinor()).isEqualTo(expectedMinor);
            assertThat(money.currency()).isEqualTo(Money.USD);
        }

        @ParameterizedTest
        @ValueSource(strings = {"12.345", "0.001", "1.999"})
        void rejectsMoreDecimalPlacesThanTheCurrencyAllowsInsteadOfRounding(String input) {
            assertThatThrownBy(() -> Money.parse(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at most 2 decimal places");
        }

        @Test
        void acceptsTrailingZerosBeyondTwoDecimalPlaces() {
            assertThat(Money.parse("12.5000").amountMinor()).isEqualTo(1250);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "12,50", "1.2.3", "NaN"})
        void rejectsNonNumericInput(String input) {
            assertThatThrownBy(() -> Money.parse(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid amount");
        }

        @Test
        void rejectsAmountsThatDoNotFitInALong() {
            assertThatThrownBy(() -> Money.parse("92233720368547758.08"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullInput() {
            assertThatThrownBy(() -> Money.parse(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Money.of(null, Money.USD)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void respectsTheCurrencysFractionDigits() {
            Currency yen = Currency.getInstance("JPY");

            assertThat(Money.parse("500", yen).amountMinor()).isEqualTo(500);
            assertThatThrownBy(() -> Money.parse("500.5", yen)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void createsFromBigDecimal() {
            assertThat(Money.of(new BigDecimal("19.99"), Money.USD)).isEqualTo(Money.ofMinor(1999));
        }
    }

    @Nested
    class Formatting {

        @ParameterizedTest
        @CsvSource({
                "1250, 12.50",
                "1, 0.01",
                "0, 0.00",
                "-307, -3.07",
                "100000000, 1000000.00"
        })
        void formatsWithExactlyTwoDecimalPlaces(long minor, String expected) {
            assertThat(Money.ofMinor(minor).toDecimalString()).isEqualTo(expected);
        }

        @Test
        void neverUsesScientificNotation() {
            assertThat(Money.ofMinor(Long.MAX_VALUE).toDecimalString()).isEqualTo("92233720368547758.07");
        }

        @Test
        void roundTripsThroughParse() {
            Money original = Money.ofMinor(123_456_789);

            assertThat(Money.parse(original.toDecimalString())).isEqualTo(original);
        }

        @Test
        void toStringIncludesCurrencyCode() {
            assertThat(Money.ofMinor(1250)).hasToString("USD 12.50");
        }

        @Test
        void exposesBigDecimalWithCurrencyScale() {
            BigDecimal value = Money.ofMinor(5).toBigDecimal();

            assertThat(value).isEqualByComparingTo("0.05");
            assertThat(value.scale()).isEqualTo(2);
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void addsAndSubtracts() {
            Money a = Money.ofMinor(1050);
            Money b = Money.ofMinor(325);

            assertThat(a.plus(b)).isEqualTo(Money.ofMinor(1375));
            assertThat(a.minus(b)).isEqualTo(Money.ofMinor(725));
            assertThat(b.minus(a)).isEqualTo(Money.ofMinor(-725));
        }

        @Test
        void negatesAndTakesAbsoluteValue() {
            assertThat(Money.ofMinor(500).negate()).isEqualTo(Money.ofMinor(-500));
            assertThat(Money.ofMinor(-500).abs()).isEqualTo(Money.ofMinor(500));
            assertThat(Money.ofMinor(500).abs()).isEqualTo(Money.ofMinor(500));
        }

        @Test
        void isImmutable() {
            Money a = Money.ofMinor(100);

            a.plus(Money.ofMinor(1));

            assertThat(a.amountMinor()).isEqualTo(100);
        }

        @Test
        void sumOfSplitPartsEqualsTheWholeWithoutFloatingPointDrift() {
            // 0.1 + 0.2 != 0.3 in floating point; with minor units it is exact.
            Money sum = Money.parse("0.10").plus(Money.parse("0.20"));

            assertThat(sum).isEqualTo(Money.parse("0.30"));
        }

        @Test
        void accumulatesManySmallAmountsExactly() {
            List<Money> cents = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                cents.add(Money.parse("0.01"));
            }

            Money total = cents.stream().reduce(Money.zero(), Money::plus);

            assertThat(total).isEqualTo(Money.parse("100.00"));
        }

        @Test
        void failsLoudlyOnOverflowInsteadOfWrappingAround() {
            Money max = Money.ofMinor(Long.MAX_VALUE);
            Money min = Money.ofMinor(Long.MIN_VALUE);

            assertThatThrownBy(() -> max.plus(Money.ofMinor(1))).isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(() -> min.minus(Money.ofMinor(1))).isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(min::negate).isInstanceOf(ArithmeticException.class);
        }

        @Test
        void refusesToMixCurrencies() {
            Money usd = Money.ofMinor(100);
            Money eur = Money.ofMinor(100, EUR);

            assertThatThrownBy(() -> usd.plus(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
            assertThatThrownBy(() -> usd.minus(eur)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> usd.compareTo(eur)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class SignAndComparison {

        @Test
        void reportsSign() {
            assertThat(Money.zero().isZero()).isTrue();
            assertThat(Money.ofMinor(1).isPositive()).isTrue();
            assertThat(Money.ofMinor(-1).isNegative()).isTrue();
            assertThat(Money.ofMinor(1).isNegative()).isFalse();
            assertThat(Money.ofMinor(-1).isPositive()).isFalse();
            assertThat(Money.ofMinor(1).isZero()).isFalse();
        }

        @Test
        void comparesByAmount() {
            assertThat(Money.ofMinor(100)).isLessThan(Money.ofMinor(101));
            assertThat(Money.ofMinor(-5)).isLessThan(Money.zero());
            assertThat(Money.ofMinor(100)).isEqualByComparingTo(Money.parse("1.00"));
        }

        @Test
        void equalityIncludesCurrency() {
            assertThat(Money.ofMinor(100)).isEqualTo(Money.ofMinor(100, Money.USD));
            assertThat(Money.ofMinor(100)).isNotEqualTo(Money.ofMinor(100, EUR));
            assertThat(Money.ofMinor(100)).hasSameHashCodeAs(Money.ofMinor(100));
        }

        @Test
        void requiresACurrency() {
            assertThatThrownBy(() -> Money.ofMinor(1, null)).isInstanceOf(NullPointerException.class);
        }
    }
}
