package com.glitchbytez.splitwise.expense;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filters for listing expenses; {@code null} means "no restriction".
 *
 * @param from inclusive start date
 * @param to   inclusive end date
 * @param q    case-insensitive text to find in the description
 */
public record ExpenseFilter(UUID paidBy, ExpenseCategory category, LocalDate from, LocalDate to, String q) {
}
