package com.glitchbytez.splitwise.expense.split;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Property-style checks of the {@link SplitStrategy} contract over thousands of random but reproducible
 * (seeded) inputs per split type: one non-negative share per participant, in input order, summing exactly to the total.
 */
class SplitInvariantsTest {

    private static final int CASES_PER_TYPE = 2_000;
    private static final long SEED = 20261022L;

    @ParameterizedTest
    @EnumSource(SplitType.class)
    void sharesAreNonNegativeOnePerParticipantAndSumToTheTotal(SplitType type) {
        Random random = new Random(SEED + type.ordinal());
        SplitStrategy strategy = SplitStrategy.forType(type);

        for (int i = 0; i < CASES_PER_TYPE; i++) {
            long total = 1 + (long) (random.nextDouble() * random.nextDouble() * 100_000_000L);
            int count = 1 + random.nextInt(20);
            List<SplitInput> participants = participants(type, total, count, random);
            String context = type + " case " + i + ": total=" + total + ", participants=" + participants;

            List<Share> shares = strategy.split(total, participants);

            assertThat(shares).as(context).extracting(Share::userId)
                    .containsExactlyElementsOf(participants.stream().map(SplitInput::userId).toList());
            assertThat(shares).as(context).allSatisfy(share -> assertThat(share.amountMinor()).isNotNegative());
            assertThat(shares.stream().mapToLong(Share::amountMinor).sum()).as(context).isEqualTo(total);
        }
    }

    private static List<SplitInput> participants(SplitType type, long total, int count, Random random) {
        List<UUID> ids = Stream.generate(UUID::randomUUID).limit(count).toList();
        return switch (type) {
            case EQUAL -> ids.stream().map(SplitInput::equal).toList();
            case EXACT -> {
                long[] parts = randomPartition(total, count, random);
                List<SplitInput> result = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    result.add(SplitInput.exact(ids.get(i), parts[i]));
                }
                yield result;
            }
            case PERCENTAGE -> {
                long[] parts = randomPartition(PercentageSplitStrategy.FULL_BP, count, random);
                List<SplitInput> result = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    result.add(SplitInput.percentage(ids.get(i), (int) parts[i]));
                }
                yield result;
            }
        };
    }

    /** Splits {@code whole} into {@code count} non-negative parts that sum to it. */
    private static long[] randomPartition(long whole, int count, Random random) {
        long[] cuts = LongStream.concat(
                        LongStream.of(0, whole),
                        LongStream.generate(() -> (long) (random.nextDouble() * (whole + 1))).limit(count - 1))
                .sorted()
                .toArray();
        long[] parts = new long[count];
        for (int i = 0; i < count; i++) {
            parts[i] = cuts[i + 1] - cuts[i];
        }
        return parts;
    }
}
