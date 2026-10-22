package com.glitchbytez.splitwise.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.glitchbytez.splitwise.activity.ActivityService;
import com.glitchbytez.splitwise.activity.ActivityType;
import com.glitchbytez.splitwise.common.error.BadRequestException;
import com.glitchbytez.splitwise.common.error.ConflictException;
import com.glitchbytez.splitwise.common.error.ForbiddenException;
import com.glitchbytez.splitwise.common.error.NotFoundException;
import com.glitchbytez.splitwise.expense.split.Share;
import com.glitchbytez.splitwise.expense.split.SplitType;
import com.glitchbytez.splitwise.group.GroupAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final UUID GROUP = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Mock
    ExpenseRepository expenses;
    @Mock
    GroupAccessService access;
    @Mock
    ActivityService activity;

    ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenses, access, activity, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class Create {

        @Test
        void computesSharesStoresTheExpenseAndLogsIt() {
            UUID expenseId = UUID.randomUUID();
            when(expenses.saveAndFlush(any(Expense.class))).thenAnswer(inv -> withId(inv.getArgument(0), expenseId, 0));

            ExpenseResponse response = service.create(GROUP, ALICE, equalRequest("10.00", null));

            assertThat(response.amount()).isEqualTo("10.00");
            assertThat(response.currency()).isEqualTo("USD");
            assertThat(response.createdBy()).isEqualTo(ALICE);
            assertThat(response.shares()).extracting(ShareResponse::amount).containsExactlyInAnyOrder("5.00", "5.00");
            verify(access).requireMember(GROUP, ALICE);
            verify(activity).record(GROUP, ALICE, ActivityType.EXPENSE_ADDED, "EXPENSE", expenseId,
                    Map.of("description", "Dinner", "amount", "10.00"));
        }

        @Test
        void checksThatThePayerAndEveryParticipantAreMembers() {
            when(expenses.saveAndFlush(any(Expense.class))).thenAnswer(inv -> withId(inv.getArgument(0), UUID.randomUUID(), 0));

            service.create(GROUP, ALICE, equalRequest("10.00", null));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UUID>> involved = ArgumentCaptor.forClass(List.class);
            verify(access).requireAllMembers(eq(GROUP), involved.capture());
            assertThat(involved.getValue()).containsExactlyInAnyOrder(ALICE, BOB, ALICE);
        }

        @Test
        void rejectsANonMemberPayerOrParticipantWithoutSaving() {
            doThrow(new BadRequestException("Not members of this group: x"))
                    .when(access).requireAllMembers(eq(GROUP), anyCollection());

            assertThatThrownBy(() -> service.create(GROUP, ALICE, equalRequest("10.00", null)))
                    .isInstanceOf(BadRequestException.class);
            verify(expenses, never()).saveAndFlush(any());
            verifyNoInteractions(activity);
        }

        @Test
        void turnsAnInconsistentSplitIntoABadRequest() {
            ExpenseRequest request = request(SplitType.EXACT, "10.00", null,
                    List.of(new ParticipantRequest(ALICE, new BigDecimal("6.00"), null),
                            new ParticipantRequest(BOB, new BigDecimal("3.00"), null)));

            assertThatThrownBy(() -> service.create(GROUP, ALICE, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("The amounts add up to 9.00 but the total is 10.00");
        }

        @Test
        void convertsPercentagesToBasisPoints() {
            when(expenses.saveAndFlush(any(Expense.class))).thenAnswer(inv -> withId(inv.getArgument(0), UUID.randomUUID(), 0));
            ExpenseRequest request = request(SplitType.PERCENTAGE, "90.00", null,
                    List.of(new ParticipantRequest(ALICE, null, new BigDecimal("66.67")),
                            new ParticipantRequest(BOB, null, new BigDecimal("33.33"))));

            ExpenseResponse response = service.create(GROUP, ALICE, request);

            assertThat(response.shares()).extracting(ShareResponse::percentage).containsExactlyInAnyOrder("66.67", "33.33");
            assertThat(response.shares()).extracting(ShareResponse::amount).containsExactlyInAnyOrder("60.00", "30.00");
        }

        @Test
        void requiresTheCallerToBeAMember() {
            when(access.requireMember(GROUP, ALICE)).thenThrow(new ForbiddenException("no"));

            assertThatThrownBy(() -> service.create(GROUP, ALICE, equalRequest("10.00", null)))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(expenses);
        }
    }

    @Nested
    class Update {

        Expense existing;

        @BeforeEach
        void setUp() {
            existing = Expense.create(GROUP, ALICE, details(1000), List.of(
                    new Share(ALICE, 500, null),
                    new Share(BOB, 500, null)), NOW.minusSeconds(60));
            withId(existing, UUID.randomUUID(), 3);
        }

        @Test
        void replacesTheExpenseWhenTheVersionMatches() {
            when(expenses.findByIdAndGroupIdAndDeletedAtIsNull(existing.getId(), GROUP)).thenReturn(Optional.of(existing));

            ExpenseResponse response = service.update(GROUP, existing.getId(), BOB, equalRequest("30.00", 3L));

            assertThat(response.amount()).isEqualTo("30.00");
            assertThat(existing.getShares()).extracting(ExpenseShare::getShareMinor).containsExactlyInAnyOrder(1500L, 1500L);
            assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
            verify(activity).record(eq(GROUP), eq(BOB), eq(ActivityType.EXPENSE_UPDATED), eq("EXPENSE"),
                    eq(existing.getId()), any());
        }

        @Test
        void rejectsAStaleVersion() {
            when(expenses.findByIdAndGroupIdAndDeletedAtIsNull(existing.getId(), GROUP)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.update(GROUP, existing.getId(), BOB, equalRequest("30.00", 2L)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage(ExpenseService.STALE_VERSION);
            assertThat(existing.getAmountMinor()).isEqualTo(1000);
        }

        @Test
        void requiresAVersion() {
            when(expenses.findByIdAndGroupIdAndDeletedAtIsNull(existing.getId(), GROUP)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.update(GROUP, existing.getId(), BOB, equalRequest("30.00", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(ExpenseService.VERSION_REQUIRED);
        }

        @Test
        void reportsAMissingOrDeletedExpenseAsNotFound() {
            UUID unknown = UUID.randomUUID();
            when(expenses.findByIdAndGroupIdAndDeletedAtIsNull(unknown, GROUP)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(GROUP, unknown, BOB, equalRequest("30.00", 0L)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage(ExpenseService.EXPENSE_NOT_FOUND);
        }

        @Test
        void deleteIsSoftAndLogged() {
            when(expenses.findByIdAndGroupIdAndDeletedAtIsNull(existing.getId(), GROUP)).thenReturn(Optional.of(existing));

            service.delete(GROUP, existing.getId(), BOB);

            assertThat(existing.getDeletedAt()).isEqualTo(NOW);
            verify(activity).record(GROUP, BOB, ActivityType.EXPENSE_DELETED, "EXPENSE", existing.getId(),
                    Map.of("description", "Dinner", "amount", "10.00"));
        }
    }

    @Nested
    class ListExpenses {

        @Test
        void rejectsAnInvertedDateRange() {
            var filter = new ExpenseFilter(null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null);

            assertThatThrownBy(() -> service.list(GROUP, ALICE, filter, PageRequest.of(0, 20)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("'from' must not be after 'to'");
        }

        @Test
        void rejectsUnknownSortProperties() {
            var filter = new ExpenseFilter(null, null, null, null, null);

            assertThatThrownBy(() -> service.list(GROUP, ALICE, filter, PageRequest.of(0, 20, Sort.by("expenseDate"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cannot sort by 'expenseDate'. Allowed: amount, createdAt, date");
        }
    }

    private static ExpenseRequest equalRequest(String amount, Long version) {
        return request(SplitType.EQUAL, amount, version,
                List.of(new ParticipantRequest(ALICE, null, null), new ParticipantRequest(BOB, null, null)));
    }

    private static ExpenseRequest request(SplitType type, String amount, Long version,
                                          List<ParticipantRequest> participants) {
        return new ExpenseRequest(" Dinner ", new BigDecimal(amount), LocalDate.of(2026, 10, 10), ALICE,
                ExpenseCategory.FOOD, null, type, participants, version);
    }

    private static ExpenseDetails details(long amountMinor) {
        return new ExpenseDetails("Dinner", amountMinor, LocalDate.of(2026, 10, 10), ALICE, ExpenseCategory.FOOD,
                null, SplitType.EQUAL);
    }

    private static Expense withId(Expense expense, UUID id, long version) {
        ReflectionTestUtils.setField(expense, "id", id);
        ReflectionTestUtils.setField(expense, "version", version);
        return expense;
    }
}
