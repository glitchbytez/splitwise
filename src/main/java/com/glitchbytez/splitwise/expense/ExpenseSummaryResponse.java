package com.glitchbytez.splitwise.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.glitchbytez.splitwise.expense.split.SplitType;

/** List item: the expense without its shares (fetch the expense for those). */
public record ExpenseSummaryResponse(
        UUID id,
        String description,
        String amount,
        LocalDate date,
        UUID paidBy,
        ExpenseCategory category,
        SplitType splitType,
        Instant createdAt) {

    static ExpenseSummaryResponse from(Expense expense) {
        return new ExpenseSummaryResponse(expense.getId(), expense.getDescription(),
                expense.getAmount().toDecimalString(), expense.getExpenseDate(), expense.getPaidBy(),
                expense.getCategory(), expense.getSplitType(), expense.getCreatedAt());
    }
}
