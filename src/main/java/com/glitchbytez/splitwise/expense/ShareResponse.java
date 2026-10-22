package com.glitchbytez.splitwise.expense;

import java.util.UUID;

import com.glitchbytez.splitwise.common.money.Money;
import com.glitchbytez.splitwise.expense.split.BasisPoints;

/** @param percentage only set for PERCENTAGE splits */
public record ShareResponse(UUID userId, String amount, String percentage) {

    static ShareResponse from(ExpenseShare share) {
        return new ShareResponse(
                share.getUserId(),
                Money.ofMinor(share.getShareMinor()).toDecimalString(),
                share.getPercentageBp() == null ? null : BasisPoints.toPercentage(share.getPercentageBp()));
    }
}
