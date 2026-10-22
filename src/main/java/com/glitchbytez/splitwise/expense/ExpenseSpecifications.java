package com.glitchbytez.splitwise.expense;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the expense list. Each returns {@code null} (= no restriction) when its input is absent. */
final class ExpenseSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ExpenseSpecifications() {
    }

    static Specification<Expense> forFilter(UUID groupId, ExpenseFilter filter) {
        // Absent filters are null; Specification.allOf does not accept null entries.
        return Specification.allOf(Stream.of(
                        inGroup(groupId),
                        notDeleted(),
                        paidBy(filter.paidBy()),
                        inCategory(filter.category()),
                        onOrAfter(filter.from()),
                        onOrBefore(filter.to()),
                        descriptionContains(filter.q()))
                .filter(Objects::nonNull)
                .toList());
    }

    static Specification<Expense> inGroup(UUID groupId) {
        return (root, query, cb) -> cb.equal(root.get("groupId"), groupId);
    }

    static Specification<Expense> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    static Specification<Expense> paidBy(UUID userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("paidBy"), userId);
    }

    static Specification<Expense> inCategory(ExpenseCategory category) {
        return category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    static Specification<Expense> onOrAfter(LocalDate from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), from);
    }

    static Specification<Expense> onOrBefore(LocalDate to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), to);
    }

    /** Case-insensitive substring match; the user's {@code %} and {@code _} are matched literally. */
    static Specification<Expense> descriptionContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
