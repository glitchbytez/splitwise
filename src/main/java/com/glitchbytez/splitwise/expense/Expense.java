package com.glitchbytez.splitwise.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.glitchbytez.splitwise.common.money.Money;
import com.glitchbytez.splitwise.expense.split.Share;
import com.glitchbytez.splitwise.expense.split.SplitType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An expense paid by one member and owed by its participants. Invariant (enforced by the split strategies before
 * anything reaches this class, and re-checked here): the shares sum exactly to the amount.
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = Money.USD.getCurrencyCode();

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "paid_by", nullable = false)
    private UUID paidBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ExpenseCategory category;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set explicitly on every change so an edit always bumps {@link #version}, even if only shares changed. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {
        // for JPA
    }

    static Expense create(UUID groupId, UUID createdBy, ExpenseDetails details, List<Share> shares, Instant now) {
        Expense expense = new Expense();
        expense.groupId = groupId;
        expense.createdBy = createdBy;
        expense.apply(details, shares, now);
        return expense;
    }

    void update(ExpenseDetails details, List<Share> newShares, Instant now) {
        apply(details, newShares, now);
    }

    void markDeleted(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    private void apply(ExpenseDetails details, List<Share> newShares, Instant now) {
        long sum = newShares.stream().mapToLong(Share::amountMinor).sum();
        if (sum != details.amountMinor()) {
            throw new IllegalStateException("Shares sum to " + sum + " but the amount is " + details.amountMinor());
        }
        this.description = details.description();
        this.amountMinor = details.amountMinor();
        this.expenseDate = details.date();
        this.paidBy = details.paidBy();
        this.category = details.category();
        this.notes = details.notes();
        this.splitType = details.splitType();
        this.updatedAt = now;
        replaceShares(newShares);
    }

    /**
     * Updates shares in place by user id instead of clearing and re-inserting: Hibernate flushes inserts before
     * deletes, so re-inserting a share for the same user would hit the (expense_id, user_id) unique constraint.
     */
    private void replaceShares(List<Share> newShares) {
        Map<UUID, Share> byUser = newShares.stream().collect(Collectors.toMap(Share::userId, Function.identity()));
        shares.removeIf(existing -> !byUser.containsKey(existing.getUserId()));
        Map<UUID, ExpenseShare> existingByUser = shares.stream()
                .collect(Collectors.toMap(ExpenseShare::getUserId, Function.identity()));
        for (Share share : newShares) {
            ExpenseShare existing = existingByUser.get(share.userId());
            if (existing != null) {
                existing.change(share.amountMinor(), share.percentageBp());
            } else {
                shares.add(new ExpenseShare(this, share.userId(), share.amountMinor(), share.percentageBp()));
            }
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getDescription() {
        return description;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor);
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public String getNotes() {
        return notes;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public long getVersion() {
        return version;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public List<ExpenseShare> getShares() {
        return Collections.unmodifiableList(shares);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Expense other && id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Expense[id=" + id + ", groupId=" + groupId + "]";
    }
}
