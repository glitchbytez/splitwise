package com.glitchbytez.splitwise.expense;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.glitchbytez.splitwise.activity.ActivityService;
import com.glitchbytez.splitwise.activity.ActivityType;
import com.glitchbytez.splitwise.common.error.BadRequestException;
import com.glitchbytez.splitwise.common.error.ConflictException;
import com.glitchbytez.splitwise.common.error.NotFoundException;
import com.glitchbytez.splitwise.common.money.Money;
import com.glitchbytez.splitwise.common.web.PageResponse;
import com.glitchbytez.splitwise.common.web.Pageables;
import com.glitchbytez.splitwise.expense.split.BasisPoints;
import com.glitchbytez.splitwise.expense.split.InvalidSplitException;
import com.glitchbytez.splitwise.expense.split.Share;
import com.glitchbytez.splitwise.expense.split.SplitInput;
import com.glitchbytez.splitwise.expense.split.SplitStrategy;
import com.glitchbytez.splitwise.group.GroupAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense rules (spec 3.3): only members can create, view, edit or delete expenses; the payer and every
 * participant must be active members; shares are computed by the {@link SplitStrategy} for the split type and
 * always sum to the amount. Every change writes an activity entry.
 */
@Service
public class ExpenseService {

    /** Public sort names (API) to entity properties. */
    static final Map<String, String> SORT_PROPERTIES = Map.of(
            "date", "expenseDate",
            "amount", "amountMinor",
            "createdAt", "createdAt");

    static final String EXPENSE_NOT_FOUND = "Expense not found";
    static final String VERSION_REQUIRED = "version is required when updating an expense";
    static final String STALE_VERSION = "The expense was changed by someone else; reload it and try again";
    static final String ENTITY_TYPE = "EXPENSE";

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository expenses;
    private final GroupAccessService access;
    private final ActivityService activity;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenses, GroupAccessService access, ActivityService activity,
                          Clock clock) {
        this.expenses = expenses;
        this.access = access;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse create(UUID groupId, UUID callerId, ExpenseRequest request) {
        access.requireMember(groupId, callerId);
        ExpenseDetails details = toDetails(request);
        List<Share> shares = computeShares(groupId, details, request.participants());

        Expense expense = expenses.saveAndFlush(Expense.create(groupId, callerId, details, shares, clock.instant()));
        activity.record(groupId, callerId, ActivityType.EXPENSE_ADDED, ENTITY_TYPE, expense.getId(), payload(expense));
        log.info("User {} added expense {} to group {}", callerId, expense.getId(), groupId);
        return ExpenseResponse.from(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID groupId, UUID expenseId, UUID callerId) {
        access.requireMember(groupId, callerId);
        return ExpenseResponse.from(requireExpense(groupId, expenseId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseSummaryResponse> list(UUID groupId, UUID callerId, ExpenseFilter filter,
                                                     Pageable pageable) {
        access.requireMember(groupId, callerId);
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        Pageable sorted = Pageables.translateSort(pageable, SORT_PROPERTIES,
                Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        return PageResponse.from(
                expenses.findAll(ExpenseSpecifications.forFilter(groupId, filter), sorted),
                ExpenseSummaryResponse::from);
    }

    /** Full replacement (PUT). The caller must send the version they read; a different one means a lost update. */
    @Transactional
    public ExpenseResponse update(UUID groupId, UUID expenseId, UUID callerId, ExpenseRequest request) {
        access.requireMember(groupId, callerId);
        Expense expense = requireExpense(groupId, expenseId);
        if (request.version() == null) {
            throw new BadRequestException(VERSION_REQUIRED);
        }
        if (request.version() != expense.getVersion()) {
            throw new ConflictException(STALE_VERSION);
        }
        ExpenseDetails details = toDetails(request);
        List<Share> shares = computeShares(groupId, details, request.participants());

        expense.update(details, shares, clock.instant());
        expenses.saveAndFlush(expense);
        activity.record(groupId, callerId, ActivityType.EXPENSE_UPDATED, ENTITY_TYPE, expenseId, payload(expense));
        return ExpenseResponse.from(expense);
    }

    /** Soft delete: the expense stops counting towards balances but stays in the database. */
    @Transactional
    public void delete(UUID groupId, UUID expenseId, UUID callerId) {
        access.requireMember(groupId, callerId);
        Expense expense = requireExpense(groupId, expenseId);
        expense.markDeleted(clock.instant());
        activity.record(groupId, callerId, ActivityType.EXPENSE_DELETED, ENTITY_TYPE, expenseId, payload(expense));
        log.info("User {} deleted expense {} in group {}", callerId, expenseId, groupId);
    }

    private Expense requireExpense(UUID groupId, UUID expenseId) {
        return expenses.findByIdAndGroupIdAndDeletedAtIsNull(expenseId, groupId)
                .orElseThrow(() -> new NotFoundException(EXPENSE_NOT_FOUND));
    }

    private static ExpenseDetails toDetails(ExpenseRequest request) {
        return new ExpenseDetails(
                request.description().trim(),
                toMinor(request.amount()),
                request.date(),
                request.paidBy(),
                request.category(),
                request.notes() == null || request.notes().isBlank() ? null : request.notes().trim(),
                request.splitType());
    }

    private List<Share> computeShares(UUID groupId, ExpenseDetails details, List<ParticipantRequest> participants) {
        List<UUID> involved = new ArrayList<>(participants.stream().map(ParticipantRequest::userId).toList());
        involved.add(details.paidBy());
        access.requireAllMembers(groupId, involved);
        try {
            List<SplitInput> inputs = participants.stream().map(ExpenseService::toSplitInput).toList();
            return SplitStrategy.forType(details.splitType()).split(details.amountMinor(), inputs);
        } catch (InvalidSplitException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static SplitInput toSplitInput(ParticipantRequest participant) {
        return new SplitInput(
                participant.userId(),
                participant.amount() == null ? null : toMinor(participant.amount()),
                participant.percentage() == null ? null : BasisPoints.fromPercentage(participant.percentage()));
    }

    private static long toMinor(BigDecimal amount) {
        try {
            return Money.of(amount, Money.USD).amountMinor();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** Enough for the feed to describe the change without loading the expense; no personal data. */
    private static Map<String, Object> payload(Expense expense) {
        return Map.of("description", expense.getDescription(), "amount", expense.getAmount().toDecimalString());
    }
}
