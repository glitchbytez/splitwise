package com.glitchbytez.splitwise.expense.split;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Divides an expense total among participants.
 * <p>
 * Implementations are pure functions with no framework dependencies. The contract every implementation
 * guarantees (and the unit tests check) is that the returned shares are non-negative, one per participant,
 * in the participants' order, and <b>sum exactly to the total</b>.
 */
public sealed interface SplitStrategy permits EqualSplitStrategy, ExactSplitStrategy, PercentageSplitStrategy {

    /**
     * Deterministic order used to hand out leftover cents: by the user id's canonical string form, so the
     * order is the same one a person sees when reading the ids (unlike {@link UUID#compareTo}, which compares
     * signed longs).
     */
    Comparator<UUID> USER_ORDER = Comparator.comparing(UUID::toString);

    List<Share> split(long totalMinor, List<SplitInput> participants);

    static SplitStrategy forType(SplitType type) {
        return switch (type) {
            case EQUAL -> EqualSplitStrategy.INSTANCE;
            case EXACT -> ExactSplitStrategy.INSTANCE;
            case PERCENTAGE -> PercentageSplitStrategy.INSTANCE;
        };
    }

    /** Checks shared by all strategies: positive total, at least one participant, no missing or duplicate ids. */
    static void requireValidBasics(long totalMinor, List<SplitInput> participants) {
        if (totalMinor <= 0) {
            throw new InvalidSplitException("The total must be greater than zero");
        }
        if (participants == null || participants.isEmpty()) {
            throw new InvalidSplitException("At least one participant is required");
        }
        Set<UUID> seen = new HashSet<>();
        for (SplitInput participant : participants) {
            if (participant == null || participant.userId() == null) {
                throw new InvalidSplitException("Every participant needs a userId");
            }
            if (!seen.add(participant.userId())) {
                throw new InvalidSplitException("Participant " + participant.userId() + " is listed more than once");
            }
        }
    }
}
