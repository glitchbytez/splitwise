package com.glitchbytez.splitwise.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.glitchbytez.splitwise.expense.split.Share;
import com.glitchbytez.splitwise.expense.split.SplitType;
import org.junit.jupiter.api.Test;

class ExpenseTest {

    private static final UUID GROUP = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    @Test
    void updatingKeepsTheShareRowsOfRemainingParticipants() {
        Expense expense = Expense.create(GROUP, A, details(1000, SplitType.EQUAL),
                List.of(new Share(A, 500, null), new Share(B, 500, null)), NOW);
        ExpenseShare originalShareOfA = expense.getShares().getFirst();

        expense.update(details(900, SplitType.EQUAL),
                List.of(new Share(A, 300, null), new Share(C, 600, null)), NOW.plusSeconds(1));

        assertThat(expense.getShares()).extracting(ExpenseShare::getUserId).containsExactly(A, C);
        assertThat(expense.getShares().getFirst()).as("A's row is updated in place, not re-inserted")
                .isSameAs(originalShareOfA);
        assertThat(originalShareOfA.getShareMinor()).isEqualTo(300);
        assertThat(expense.getAmountMinor()).isEqualTo(900);
        assertThat(expense.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void refusesSharesThatDoNotSumToTheAmount() {
        assertThatThrownBy(() -> Expense.create(GROUP, A, details(1000, SplitType.EXACT),
                List.of(new Share(A, 500, null), new Share(B, 499, null)), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isAlwaysUsd() {
        Expense expense = Expense.create(GROUP, A, details(100, SplitType.EQUAL), List.of(new Share(A, 100, null)), NOW);

        assertThat(expense.getCurrency()).isEqualTo("USD");
        assertThat(expense.getAmount().toString()).isEqualTo("USD 1.00");
    }

    private static ExpenseDetails details(long amountMinor, SplitType splitType) {
        return new ExpenseDetails("Taxi", amountMinor, LocalDate.of(2026, 10, 1), A, null, null, splitType);
    }
}
