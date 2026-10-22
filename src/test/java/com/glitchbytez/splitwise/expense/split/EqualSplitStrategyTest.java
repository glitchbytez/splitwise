package com.glitchbytez.splitwise.expense.split;

import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.A;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.B;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.C;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.D;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EqualSplitStrategyTest {

    private final SplitStrategy strategy = SplitStrategy.forType(SplitType.EQUAL);

    @Test
    void splitsEvenlyWhenThereIsNoRemainder() {
        List<Share> shares = strategy.split(9000, List.of(SplitInput.equal(A), SplitInput.equal(B), SplitInput.equal(C)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(3000L, 3000L, 3000L);
    }

    @Test
    void givesLeftoverCentsToTheFirstParticipantsByUserId() {
        // 10.00 / 3 = 3.33 remainder 1 cent -> A gets it
        List<Share> shares = strategy.split(1000, List.of(SplitInput.equal(C), SplitInput.equal(A), SplitInput.equal(B)));

        assertThat(shares).extracting(Share::userId).containsExactly(C, A, B);
        assertThat(shares).extracting(Share::amountMinor).containsExactly(333L, 334L, 333L);
    }

    @Test
    void distributesSeveralLeftoverCentsInUserIdOrder() {
        // 0.11 / 4 = 0.02 remainder 3 -> A, B, C get 0.03, D gets 0.02
        List<Share> shares = strategy.split(11,
                List.of(SplitInput.equal(D), SplitInput.equal(C), SplitInput.equal(B), SplitInput.equal(A)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(2L, 3L, 3L, 3L);
    }

    @Test
    void isIndependentOfInputOrder() {
        List<Share> one = strategy.split(1000, List.of(SplitInput.equal(A), SplitInput.equal(B), SplitInput.equal(C)));
        List<Share> other = strategy.split(1000, List.of(SplitInput.equal(B), SplitInput.equal(C), SplitInput.equal(A)));

        assertThat(other).containsExactlyInAnyOrderElementsOf(one);
    }

    @Test
    void aSingleParticipantGetsEverything() {
        assertThat(strategy.split(4321, List.of(SplitInput.equal(A))))
                .containsExactly(new Share(A, 4321, null));
    }

    @Test
    void moreParticipantsThanCentsLeavesSomeAtZero() {
        List<Share> shares = strategy.split(2, List.of(SplitInput.equal(A), SplitInput.equal(B), SplitInput.equal(C)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(1L, 1L, 0L);
    }

    @Test
    void handlesVeryLargeAmounts() {
        long total = Long.MAX_VALUE;
        List<Share> shares = strategy.split(total, List.of(SplitInput.equal(A), SplitInput.equal(B), SplitInput.equal(C)));

        assertThat(shares.stream().mapToLong(Share::amountMinor).sum()).isEqualTo(total);
    }

    @ParameterizedTest(name = "{0} cents among {1}")
    @CsvSource({"1, 1", "1, 7", "100, 3", "101, 3", "99999, 7", "1000000, 13", "123456789, 50"})
    void sharesAlwaysSumToTheTotalAndDifferByAtMostOneCent(long total, int count) {
        List<SplitInput> participants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            participants.add(SplitInput.equal(UUID.randomUUID()));
        }

        List<Share> shares = strategy.split(total, participants);

        assertThat(shares).hasSize(count);
        assertThat(shares.stream().mapToLong(Share::amountMinor).sum()).isEqualTo(total);
        long max = shares.stream().mapToLong(Share::amountMinor).max().orElseThrow();
        long min = shares.stream().mapToLong(Share::amountMinor).min().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    void rejectsAmountsOrPercentagesOnParticipants() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.exact(A, 1000))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("EQUAL split takes participants only");
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.percentage(A, 10000))))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.equal(A), SplitInput.equal(A))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("listed more than once");
    }

    @Test
    void rejectsNoParticipants() {
        assertThatThrownBy(() -> strategy.split(1000, List.of()))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("At least one participant is required");
        assertThatThrownBy(() -> strategy.split(1000, null)).isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsAMissingUserId() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.equal(null))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Every participant needs a userId");
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "-1000"})
    void rejectsANonPositiveTotal(long total) {
        assertThatThrownBy(() -> strategy.split(total, List.of(SplitInput.equal(A))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("The total must be greater than zero");
    }
}
