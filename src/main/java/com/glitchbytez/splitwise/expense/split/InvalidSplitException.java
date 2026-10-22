package com.glitchbytez.splitwise.expense.split;

/** The participants of a split are inconsistent with its type or total. The message is safe to show to clients. */
public class InvalidSplitException extends RuntimeException {

    public InvalidSplitException(String message) {
        super(message);
    }
}
