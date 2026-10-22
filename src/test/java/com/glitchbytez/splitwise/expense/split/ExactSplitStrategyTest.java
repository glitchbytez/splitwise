package com.glitchbytez.splitwise.expense.split;

import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.A;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.B;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.C;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExactSplitStrategyTest {

    private final SplitStrategy strategy = SplitStrategy.forType(SplitType.EXACT);

    @Test
    void usesTheGivenAmounts() {
        List<Share> shares = strategy.split(9000,
                List.of(SplitInput.exact(A, 5000), SplitInput.exact(B, 2500), SplitInput.exact(C, 1500)));

        assertThat(shares).containsExactly(new Share(A, 5000, null), new Share(B, 2500, null), new Share(C, 1500, null));
    }

    @Test
    void allowsAZeroShare() {
        List<Share> shares = strategy.split(1000, List.of(SplitInput.exact(A, 1000), SplitInput.exact(B, 0)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(1000L, 0L);
    }

    @Test
    void aSingleParticipantMustCoverTheWholeTotal() {
        assertThat(strategy.split(777, List.of(SplitInput.exact(A, 777)))).containsExactly(new Share(A, 777, null));
    }

    @Test
    void rejectsAmountsThatSumToLessThanTheTotal() {
        assertThatThrownBy(() -> strategy.split(9000, List.of(SplitInput.exact(A, 5000), SplitInput.exact(B, 3999))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("The amounts add up to 89.99 but the total is 90.00");
    }

    @Test
    void rejectsAmountsThatSumToMoreThanTheTotal() {
        assertThatThrownBy(() -> strategy.split(9000, List.of(SplitInput.exact(A, 5000), SplitInput.exact(B, 4001))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("The amounts add up to 90.01 but the total is 90.00");
    }

    @Test
    void rejectsNegativeAmountsEvenIfTheSumMatches() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.exact(A, 1500), SplitInput.exact(B, -500))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("negative amount");
    }

    @Test
    void rejectsAMissingAmount() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.exact(A, 1000), SplitInput.equal(B))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Participant " + B + " needs an amount");
    }

    @Test
    void rejectsPercentages() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(new SplitInput(A, 1000L, 10000))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("not percentages");
    }

    @Test
    void rejectsSumsThatOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> strategy.split(1000,
                List.of(SplitInput.exact(A, Long.MAX_VALUE), SplitInput.exact(B, Long.MAX_VALUE))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("The amounts are too large");
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.exact(A, 500), SplitInput.exact(A, 500))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("listed more than once");
    }

    @Test
    void rejectsANonPositiveTotal() {
        assertThatThrownBy(() -> strategy.split(0, List.of(SplitInput.exact(A, 0))))
                .isInstanceOf(InvalidSplitException.class);
    }
}
