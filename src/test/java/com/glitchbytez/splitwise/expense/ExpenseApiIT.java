package com.glitchbytez.splitwise.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExpenseApiIT extends ApiIntegrationTest {

    String alice;
    String bob;
    UUID aliceId;
    UUID bobId;
    UUID carolId;
    UUID groupId;

    @BeforeEach
    void setUp() {
        alice = registerAndLogin("alice@example.com");
        bob = registerAndLogin("bob@example.com");
        String carol = registerAndLogin("carol@example.com");
        aliceId = userIdOf(alice);
        bobId = userIdOf(bob);
        carolId = userIdOf(carol);
        groupId = createGroup(alice, "Lisbon");
        addMember(alice, groupId, "bob@example.com");
        addMember(alice, groupId, "carol@example.com");
    }

    /** The core money invariant, checked in the database after every test: shares sum to the expense amount. */
    @AfterEach
    void everyExpenseSharesSumToItsAmount() {
        List<UUID> broken = jdbc.queryForList("""
                select e.id from expenses e left join expense_shares s on s.expense_id = e.id
                group by e.id, e.amount_minor
                having coalesce(sum(s.share_minor), 0) <> e.amount_minor
                """, UUID.class);
        assertThat(broken).as("expenses whose shares do not sum to the amount").isEmpty();
    }

    @Nested
    class Create {

        @Test
        void equalSplitGivesTheLeftoverCentDeterministically() {
            ValidatableResponse response = post(expense("10.00", "EQUAL",
                    List.of(participant(aliceId), participant(bobId), participant(carolId))))
                    .statusCode(201)
                    .body("amount", equalTo("10.00"))
                    .body("currency", equalTo("USD"))
                    .body("splitType", equalTo("EQUAL"))
                    .body("version", equalTo(0))
                    .body("shares", hasSize(3))
                    .body("shares.percentage", contains(nullValue(), nullValue(), nullValue()));

            List<String> amounts = response.extract().path("shares.amount");
            assertThat(amounts).containsExactlyInAnyOrder("3.34", "3.33", "3.33");
            String firstById = List.of(aliceId, bobId, carolId).stream().map(UUID::toString).sorted().findFirst().orElseThrow();
            response.body("shares.find { it.userId == '" + firstById + "' }.amount", equalTo("3.34"));
        }

        @Test
        void exactSplitUsesTheGivenAmounts() {
            post(expense("90.00", "EXACT", List.of(
                    participant(aliceId, "amount", "50.00"),
                    participant(bobId, "amount", "40.00"))))
                    .statusCode(201)
                    .body("shares.find { it.userId == '" + aliceId + "' }.amount", equalTo("50.00"))
                    .body("shares.find { it.userId == '" + bobId + "' }.amount", equalTo("40.00"));
        }

        @Test
        void percentageSplitMatchesTheSpecExample() {
            post(expense("90.00", "PERCENTAGE", List.of(
                    participant(aliceId, "percentage", "50.00"),
                    participant(bobId, "percentage", "30.00"),
                    participant(carolId, "percentage", "20.00"))))
                    .statusCode(201)
                    .body("shares.find { it.userId == '" + aliceId + "' }.amount", equalTo("45.00"))
                    .body("shares.find { it.userId == '" + bobId + "' }.amount", equalTo("27.00"))
                    .body("shares.find { it.userId == '" + carolId + "' }.amount", equalTo("18.00"))
                    .body("shares.find { it.userId == '" + carolId + "' }.percentage", equalTo("20.00"));
        }

        @Test
        void acceptsAmountsAsJsonNumbersToo() {
            Map<String, Object> body = expense("12.5", "EQUAL", List.of(participant(aliceId)));
            body.put("amount", 12.5);

            post(body).statusCode(201).body("amount", equalTo("12.50"));
        }

        @Test
        void thePayerDoesNotHaveToBeAParticipant() {
            Map<String, Object> body = expense("20.00", "EQUAL", List.of(participant(bobId), participant(carolId)));

            post(body).statusCode(201)
                    .body("paidBy", equalTo(aliceId.toString()))
                    .body("shares.userId", containsInAnyOrder(bobId.toString(), carolId.toString()));
        }

        @Test
        void returnsTheLocationOfTheNewExpense() {
            String location = authenticated(alice).body(expense("1.00", "EQUAL", List.of(participant(aliceId))))
                    .post("/api/groups/{g}/expenses", groupId)
                    .then().statusCode(201).extract().header("Location");

            assertThat(location).startsWith("/api/groups/" + groupId + "/expenses/");
            authenticated(bob).get(location).then().statusCode(200);
        }

        @Test
        void rejectsAnInconsistentSplitWithAClearMessage() {
            post(expense("90.00", "PERCENTAGE", List.of(
                    participant(aliceId, "percentage", "50.00"),
                    participant(bobId, "percentage", "49.99"))))
                    .statusCode(400)
                    .contentType("application/problem+json")
                    .body("detail", equalTo("The percentages add up to 99.99 but must add up to exactly 100.00"));
        }

        @Test
        void rejectsAPayerOrParticipantWhoIsNotAMember() {
            String dave = registerAndLogin("dave@example.com");
            UUID daveId = userIdOf(dave);

            post(expense("10.00", "EQUAL", List.of(participant(aliceId), participant(daveId))))
                    .statusCode(400)
                    .body("detail", equalTo("Not members of this group: " + daveId));

            Map<String, Object> paidByOutsider = expense("10.00", "EQUAL", List.of(participant(aliceId)));
            paidByOutsider.put("paidBy", daveId.toString());
            post(paidByOutsider).statusCode(400);
        }

        @Test
        void rejectsAFormerMemberAsParticipant() {
            authenticated(bob).delete("/api/groups/{g}/members/{u}", groupId, bobId).then().statusCode(204);

            post(expense("10.00", "EQUAL", List.of(participant(aliceId), participant(bobId)))).statusCode(400);
        }

        @Test
        void validatesTheRequestFields() {
            Map<String, Object> body = new HashMap<>();
            body.put("description", "");
            body.put("amount", "0.00");
            body.put("participants", List.of());

            post(body).statusCode(400)
                    .body("errors.field", hasItem("description"))
                    .body("errors.field", hasItem("amount"))
                    .body("errors.field", hasItem("date"))
                    .body("errors.field", hasItem("paidBy"))
                    .body("errors.field", hasItem("splitType"))
                    .body("errors.field", hasItem("participants"));
        }

        @Test
        void rejectsMoreThanTwoDecimalPlaces() {
            post(expense("10.005", "EQUAL", List.of(participant(aliceId))))
                    .statusCode(400)
                    .body("errors.field", contains("amount"));
        }

        @Test
        void rejectsAnUnknownSplitType() {
            post(expense("10.00", "SHARES", List.of(participant(aliceId)))).statusCode(400);
        }

        @Test
        void writesAnActivityEntryVisibleInTheFeed() {
            String id = post(expense("42.00", "EQUAL", List.of(participant(aliceId)))).extract().path("id");

            authenticated(bob).get("/api/groups/{g}/activity", groupId)
                    .then().statusCode(200)
                    .body("content[0].type", equalTo("EXPENSE_ADDED"))
                    .body("content[0].entityId", equalTo(id))
                    .body("content[0].actorId", equalTo(aliceId.toString()))
                    .body("content[0].payload.amount", equalTo("42.00"))
                    .body("content[0].payload.description", equalTo("Dinner"));
        }
    }

    @Nested
    class UpdateAndDelete {

        String expenseId;

        @BeforeEach
        void createExpense() {
            expenseId = post(expense("30.00", "EQUAL", List.of(participant(aliceId), participant(bobId))))
                    .statusCode(201).extract().path("id");
        }

        @Test
        void anyMemberCanReplaceTheExpenseAndTheVersionIncrements() {
            Map<String, Object> body = expense("45.00", "EXACT", List.of(
                    participant(bobId, "amount", "15.00"), participant(carolId, "amount", "30.00")));
            body.put("version", 0);
            body.put("category", "TRANSPORT");

            authenticated(bob).body(body).put("/api/groups/{g}/expenses/{e}", groupId, expenseId)
                    .then().statusCode(200)
                    .body("amount", equalTo("45.00"))
                    .body("splitType", equalTo("EXACT"))
                    .body("category", equalTo("TRANSPORT"))
                    .body("version", equalTo(1))
                    .body("shares.userId", containsInAnyOrder(bobId.toString(), carolId.toString()));

            assertThat(jdbc.queryForObject("select count(*) from expense_shares where expense_id = ?::uuid",
                    Integer.class, expenseId)).isEqualTo(2);
        }

        @Test
        void aStaleVersionIsRejectedSoNoUpdateIsLost() {
            Map<String, Object> first = expense("31.00", "EQUAL", List.of(participant(aliceId), participant(bobId)));
            first.put("version", 0);
            authenticated(alice).body(first).put("/api/groups/{g}/expenses/{e}", groupId, expenseId)
                    .then().statusCode(200);

            Map<String, Object> second = expense("99.00", "EQUAL", List.of(participant(bobId)));
            second.put("version", 0);
            authenticated(bob).body(second).put("/api/groups/{g}/expenses/{e}", groupId, expenseId)
                    .then().statusCode(409)
                    .body("detail", equalTo("The expense was changed by someone else; reload it and try again"));

            getExpense().body("amount", equalTo("31.00"));
        }

        @Test
        void aMissingVersionIsABadRequest() {
            authenticated(alice).body(expense("31.00", "EQUAL", List.of(participant(aliceId))))
                    .put("/api/groups/{g}/expenses/{e}", groupId, expenseId)
                    .then().statusCode(400)
                    .body("detail", equalTo("version is required when updating an expense"));
        }

        @Test
        void deletingIsSoftAndTheExpenseDisappearsFromTheApi() {
            authenticated(bob).delete("/api/groups/{g}/expenses/{e}", groupId, expenseId).then().statusCode(204);

            getExpense().statusCode(404).body("detail", equalTo("Expense not found"));
            authenticated(alice).get("/api/groups/{g}/expenses", groupId).then().body("totalElements", equalTo(0));
            authenticated(alice).delete("/api/groups/{g}/expenses/{e}", groupId, expenseId).then().statusCode(404);
            assertThat(jdbc.queryForObject("select deleted_at is not null from expenses where id = ?::uuid",
                    Boolean.class, expenseId)).isTrue();
        }

        @Test
        void anExpenseIsOnlyReachableThroughItsOwnGroup() {
            UUID otherGroup = createGroup(alice, "Other");

            authenticated(alice).get("/api/groups/{g}/expenses/{e}", otherGroup, expenseId).then().statusCode(404);
        }

        @Test
        void updatesAndDeletesAreInTheActivityFeedNewestFirst() {
            Map<String, Object> body = expense("31.00", "EQUAL", List.of(participant(aliceId)));
            body.put("version", 0);
            authenticated(alice).body(body).put("/api/groups/{g}/expenses/{e}", groupId, expenseId);
            authenticated(alice).delete("/api/groups/{g}/expenses/{e}", groupId, expenseId);

            authenticated(alice).queryParam("size", 3).get("/api/groups/{g}/activity", groupId)
                    .then().statusCode(200)
                    .body("content.type", contains("EXPENSE_DELETED", "EXPENSE_UPDATED", "EXPENSE_ADDED"));
        }

        private ValidatableResponse getExpense() {
            return authenticated(alice).get("/api/groups/{g}/expenses/{e}", groupId, expenseId).then();
        }
    }

    @Nested
    class ListAndFilter {

        @BeforeEach
        void seed() {
            seedExpense("Dinner at Ramiro", "60.00", "2026-10-01", aliceId, "FOOD");
            seedExpense("Taxi to airport", "25.00", "2026-10-03", bobId, "TRANSPORT");
            seedExpense("Hotel 100% refundable", "300.00", "2026-10-02", aliceId, "ACCOMMODATION");
            seedExpense("Late dinner", "40.00", "2026-10-05", carolId, "FOOD");
            seedExpense("Groceries", "15.50", "2026-10-04", bobId, null);
        }

        @Test
        void defaultsToNewestDateFirst() {
            list(Map.of()).body("content.description", contains(
                    "Late dinner", "Groceries", "Taxi to airport", "Hotel 100% refundable", "Dinner at Ramiro"));
        }

        @Test
        void sortsByAmount() {
            list(Map.of("sort", "amount,desc")).body("content.amount",
                    contains("300.00", "60.00", "40.00", "25.00", "15.50"));
        }

        @Test
        void paginates() {
            list(Map.of("size", 2, "page", 2))
                    .body("content", hasSize(1))
                    .body("totalElements", equalTo(5))
                    .body("content[0].description", equalTo("Dinner at Ramiro"));
        }

        @Test
        void filtersByPayer() {
            list(Map.of("paidBy", aliceId)).body("content.description",
                    contains("Hotel 100% refundable", "Dinner at Ramiro"));
        }

        @Test
        void filtersByCategory() {
            list(Map.of("category", "FOOD")).body("content.description", contains("Late dinner", "Dinner at Ramiro"));
        }

        @Test
        void filtersByInclusiveDateRange() {
            list(Map.of("from", "2026-10-02", "to", "2026-10-04")).body("content.description",
                    contains("Groceries", "Taxi to airport", "Hotel 100% refundable"));
        }

        @Test
        void searchesTheDescriptionCaseInsensitively() {
            list(Map.of("q", "DINNER")).body("content.description", contains("Late dinner", "Dinner at Ramiro"));
        }

        @Test
        void treatsLikeWildcardsInTheSearchLiterally() {
            list(Map.of("q", "100%")).body("content.description", contains("Hotel 100% refundable"));
            list(Map.of("q", "_")).body("totalElements", equalTo(0));
        }

        @Test
        void combinesFilters() {
            list(Map.of("paidBy", aliceId, "category", "FOOD", "q", "ramiro"))
                    .body("content.description", contains("Dinner at Ramiro"));
        }

        @Test
        void rejectsInvalidFilterValues() {
            listRaw(Map.of("category", "CRYPTO")).statusCode(400);
            listRaw(Map.of("from", "2026-13-01")).statusCode(400);
            listRaw(Map.of("from", "2026-10-05", "to", "2026-10-01")).statusCode(400);
            listRaw(Map.of("sort", "description")).statusCode(400)
                    .body("detail", equalTo("Cannot sort by 'description'. Allowed: amount, createdAt, date"));
        }

        @Test
        void listItemsDoNotIncludeShares() {
            list(Map.of("size", 1)).body("content[0].shares", nullValue());
        }

        private void seedExpense(String description, String amount, String date, UUID paidBy, String category) {
            Map<String, Object> body = expense(amount, "EQUAL", List.of(participant(aliceId), participant(bobId)));
            body.put("description", description);
            body.put("date", date);
            body.put("paidBy", paidBy.toString());
            body.put("category", category);
            post(body).statusCode(201);
        }

        private ValidatableResponse list(Map<String, ?> params) {
            return listRaw(params).statusCode(200);
        }

        private ValidatableResponse listRaw(Map<String, ?> params) {
            return authenticated(bob).queryParams(params).get("/api/groups/{g}/expenses", groupId).then();
        }
    }

    @Nested
    class BalancesAndMembership {

        @Test
        void aMemberWhoOwesMoneyCannotLeaveUntilTheExpenseIsGone() {
            String id = post(expense("20.00", "EQUAL", List.of(participant(aliceId), participant(bobId))))
                    .extract().path("id");

            authenticated(bob).delete("/api/groups/{g}/members/{u}", groupId, bobId)
                    .then().statusCode(409)
                    .body("detail", equalTo("You can only leave once your balance in this group is zero"));
            authenticated(alice).delete("/api/groups/{g}/members/{u}", groupId, bobId)
                    .then().statusCode(409);

            authenticated(alice).delete("/api/groups/{g}/expenses/{e}", groupId, id).then().statusCode(204);
            authenticated(bob).delete("/api/groups/{g}/members/{u}", groupId, bobId).then().statusCode(204);
        }

        @Test
        void aMemberWhoOnlyParticipatedInBalancedExpensesCanLeave() {
            // Carol pays 10 for herself only: paid 10, owes 10 -> net zero.
            Map<String, Object> body = expense("10.00", "EQUAL", List.of(participant(carolId)));
            body.put("paidBy", carolId.toString());
            post(body).statusCode(201);

            authenticated(alice).delete("/api/groups/{g}/members/{u}", groupId, carolId).then().statusCode(204);
        }
    }

    private ValidatableResponse post(Map<String, Object> body) {
        return authenticated(alice).body(body).post("/api/groups/{g}/expenses", groupId).then();
    }

    private Map<String, Object> expense(String amount, String splitType, List<Map<String, Object>> participants) {
        Map<String, Object> body = new HashMap<>();
        body.put("description", "Dinner");
        body.put("amount", amount);
        body.put("date", "2026-10-10");
        body.put("paidBy", aliceId.toString());
        body.put("category", "FOOD");
        body.put("splitType", splitType);
        body.put("participants", new ArrayList<>(participants));
        return body;
    }

    private static Map<String, Object> participant(UUID userId) {
        return Map.of("userId", userId.toString());
    }

    private static Map<String, Object> participant(UUID userId, String field, String value) {
        return Map.of("userId", userId.toString(), field, value);
    }
}
