package com.glitchbytez.splitwise.user;

import java.util.Locale;

/** Emails are compared case-insensitively by storing them trimmed and lower-cased. */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
