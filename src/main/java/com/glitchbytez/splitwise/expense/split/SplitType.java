package com.glitchbytez.splitwise.expense.split;

public enum SplitType {
    /** Split among the selected participants equally; leftover cents go to the first participants by user id. */
    EQUAL,
    /** Each participant has an explicit amount; the amounts must sum to the total. */
    EXACT,
    /** Each participant has a percentage; percentages must sum to exactly 100.00. */
    PERCENTAGE
}
