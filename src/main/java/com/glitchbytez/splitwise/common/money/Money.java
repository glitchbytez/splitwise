package com.glitchbytez.splitwise.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An immutable amount of money stored as a whole number of minor units (e.g. cents).
 * <p>
 * Floating point is never used. Arithmetic is overflow-checked and only allowed between
 * amounts of the same currency. v1 is USD-only, but the currency is kept in the type so
 * multi-currency support in v2 is a small change.
 */
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

    public static final Currency USD = Currency.getInstance("USD");

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static Money ofMinor(long amountMinor) {
        return new Money(amountMinor, USD);
    }

    public static Money ofMinor(long amountMinor, Currency currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero() {
        return ofMinor(0);
    }

    /**
     * Parses a decimal string such as {@code "12.50"} into USD.
     *
     * @throws IllegalArgumentException if the value is not a number or has more decimal
     *                                  places than the currency allows
     */
    public static Money parse(String decimal) {
        return parse(decimal, USD);
    }

    public static Money parse(String decimal, Currency currency) {
        Objects.requireNonNull(decimal, "amount must not be null");
        try {
            return of(new BigDecimal(decimal.trim()), currency);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: '" + decimal + "'", e);
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        int digits = currency.getDefaultFractionDigits();
        try {
            long minor = amount.setScale(digits, RoundingMode.UNNECESSARY)
                    .movePointRight(digits)
                    .longValueExact();
            return new Money(minor, currency);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount " + amount.toPlainString() + " must have at most " + digits
                            + " decimal places and fit in the supported range", e);
        }
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public Money abs() {
        return isNegative() ? negate() : this;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits());
    }

    /** The API representation, always with the currency's number of decimals, e.g. {@code "12.50"}. */
    public String toDecimalString() {
        return toBigDecimal().toPlainString();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + toDecimalString();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }
}
