package com.glitchbytez.splitwise.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.glitchbytez.splitwise.expense.split.SplitType;

public record ExpenseResponse(
        UUID id,
        UUID groupId,
        String description,
        String amount,
        String currency,
        LocalDate date,
        UUID paidBy,
        ExpenseCategory category,
        String notes,
        SplitType splitType,
        List<ShareResponse> shares,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static ExpenseResponse from(Expense expense) {
        List<ShareResponse> shares = expense.getShares().stream()
                .sorted(Comparator.comparing(share -> share.getUserId().toString()))
                .map(ShareResponse::from)
                .toList();
        return new ExpenseResponse(expense.getId(), expense.getGroupId(), expense.getDescription(),
                expense.getAmount().toDecimalString(), expense.getCurrency(), expense.getExpenseDate(),
                expense.getPaidBy(), expense.getCategory(), expense.getNotes(), expense.getSplitType(), shares,
                expense.getCreatedBy(), expense.getCreatedAt(), expense.getUpdatedAt(), expense.getVersion());
    }
}
