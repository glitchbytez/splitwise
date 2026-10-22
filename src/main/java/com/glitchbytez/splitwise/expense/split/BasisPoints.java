package com.glitchbytez.splitwise.expense.split;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converts between percentages with up to two decimals ("33.33") and integer basis points (3333). */
public final class BasisPoints {

    private BasisPoints() {
    }

    /**
     * @throws InvalidSplitException if the percentage has more than two decimals or is outside 0..100
     */
    public static int fromPercentage(BigDecimal percentage) {
        try {
            int bp = percentage.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).intValueExact();
            if (bp < 0 || bp > PercentageSplitStrategy.FULL_BP) {
                throw new InvalidSplitException("Percentages must be between 0 and 100");
            }
            return bp;
        } catch (ArithmeticException e) {
            throw new InvalidSplitException("Percentages can have at most two decimal places");
        }
    }

    public static String toPercentage(int bp) {
        return BigDecimal.valueOf(bp, 2).toPlainString();
    }
}
