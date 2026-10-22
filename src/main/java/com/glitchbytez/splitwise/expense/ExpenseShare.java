package com.glitchbytez.splitwise.expense;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** What one participant owes for an expense. */
@Entity
@Table(name = "expense_shares")
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false, updatable = false)
    private Expense expense;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "share_minor", nullable = false)
    private long shareMinor;

    @Column(name = "percentage_bp")
    private Integer percentageBp;

    protected ExpenseShare() {
        // for JPA
    }

    ExpenseShare(Expense expense, UUID userId, long shareMinor, Integer percentageBp) {
        this.expense = expense;
        this.userId = userId;
        this.shareMinor = shareMinor;
        this.percentageBp = percentageBp;
    }

    void change(long shareMinor, Integer percentageBp) {
        this.shareMinor = shareMinor;
        this.percentageBp = percentageBp;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getShareMinor() {
        return shareMinor;
    }

    public Integer getPercentageBp() {
        return percentageBp;
    }
}
