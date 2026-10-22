package com.glitchbytez.splitwise.expense.split;

import java.util.UUID;

/** Fixed ids whose string order is A < B < C < D, so leftover-cent assertions are readable. */
final class SplitTestUsers {

    static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    static final UUID D = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private SplitTestUsers() {
    }
}
