package com.glitchbytez.splitwise.expense;

import java.time.LocalDate;
import java.util.UUID;

import com.glitchbytez.splitwise.expense.split.SplitType;

/** The validated, user-editable fields of an expense (everything except its shares). */
record ExpenseDetails(
        String description,
        long amountMinor,
        LocalDate date,
        UUID paidBy,
        ExpenseCategory category,
        String notes,
        SplitType splitType) {
}
