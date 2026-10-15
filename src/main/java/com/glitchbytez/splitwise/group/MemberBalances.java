package com.glitchbytez.splitwise.group;

import java.util.UUID;

/**
 * What the group feature needs to know about balances, without depending on how they are computed.
 * Membership rules (spec 3.2) only allow leaving or removing a member whose balance in the group is zero.
 */
public interface MemberBalances {

    boolean isSettled(UUID groupId, UUID userId);
}
