package com.glitchbytez.splitwise.group;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.UUID;
import java.util.stream.Stream;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import io.restassured.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 2 acceptance: non-members cannot see or touch a group, and owner-only actions are enforced.
 * Every group endpoint is listed here; new group-scoped endpoints in later phases should be added too.
 */
class GroupAuthorizationIT extends ApiIntegrationTest {

    String owner;
    String member;
    String outsider;
    UUID ownerId;
    UUID memberId;
    UUID groupId;
    UUID expenseId;

    @BeforeEach
    void setUp() {
        owner = registerAndLogin("owner@example.com");
        member = registerAndLogin("member@example.com");
        outsider = registerAndLogin("outsider@example.com");
        registerAndLogin("someone@example.com");
        ownerId = userIdOf(owner);
        memberId = userIdOf(member);
        groupId = createGroup(owner, "Private");
        addMember(owner, groupId, "member@example.com");
        expenseId = UUID.fromString(authenticated(owner).body(expenseBody(ownerId, null))
                .post("/api/groups/{g}/expenses", groupId)
                .then().statusCode(201).extract().path("id"));
    }

    private static String expenseBody(UUID payer, Integer version) {
        return """
                {"description": "Taxi", "amount": "10.00", "date": "2026-10-10", "paidBy": "%s",
                 "splitType": "EQUAL", "participants": [{"userId": "%s"}]%s}
                """.formatted(payer, payer, version == null ? "" : ", \"version\": " + version);
    }

    /**
     * {@code {member}}, {@code {owner}} and {@code {expense}} placeholders are replaced with the corresponding ids;
     * {@code BODY_EXPENSE} with a valid expense body.
     */
    static Stream<Arguments> allGroupEndpoints() {
        return Stream.of(
                Arguments.of(Method.GET, "/api/groups/{groupId}/activity", "{}"),
                Arguments.of(Method.POST, "/api/groups/{groupId}/expenses", BODY_EXPENSE),
                Arguments.of(Method.GET, "/api/groups/{groupId}/expenses", "{}"),
                Arguments.of(Method.GET, "/api/groups/{groupId}/expenses/{expense}", "{}"),
                Arguments.of(Method.PUT, "/api/groups/{groupId}/expenses/{expense}", BODY_EXPENSE),
                Arguments.of(Method.DELETE, "/api/groups/{groupId}/expenses/{expense}", "{}"),
                Arguments.of(Method.GET, "/api/groups/{groupId}", "{}"),
                Arguments.of(Method.PATCH, "/api/groups/{groupId}", "{\"name\": \"Hijacked\"}"),
                Arguments.of(Method.DELETE, "/api/groups/{groupId}", "{}"),
                Arguments.of(Method.POST, "/api/groups/{groupId}/members", "{\"email\": \"someone@example.com\"}"),
                Arguments.of(Method.DELETE, "/api/groups/{groupId}/members/{member}", "{}"),
                Arguments.of(Method.DELETE, "/api/groups/{groupId}/members/{owner}", "{}"));
    }

    static Stream<Arguments> ownerOnlyEndpoints() {
        return Stream.of(
                Arguments.of(Method.PATCH, "/api/groups/{groupId}", "{\"name\": \"Hijacked\"}"),
                Arguments.of(Method.DELETE, "/api/groups/{groupId}", "{}"),
                Arguments.of(Method.DELETE, "/api/groups/{groupId}/members/{owner}", "{}"));
    }

    private static final String BODY_EXPENSE = "BODY_EXPENSE";

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allGroupEndpoints")
    void nonMembersAreForbidden(Method method, String path, String body) {
        authenticated(outsider).body(resolveBody(body)).request(method, resolve(path))
                .then().statusCode(403)
                .contentType("application/problem+json")
                .body("detail", equalTo("You are not a member of this group"));

        assertGroupUntouched();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("allGroupEndpoints")
    void unknownGroupsAreNotFound(Method method, String path, String body) {
        authenticated(owner).body(resolveBody(body)).request(method, path
                        .replace("{groupId}", UUID.randomUUID().toString())
                        .replace("{member}", memberId.toString())
                        .replace("{owner}", ownerId.toString())
                        .replace("{expense}", expenseId.toString()))
                .then().statusCode(404)
                .body("detail", equalTo("Group not found"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ownerOnlyEndpoints")
    void plainMembersCannotPerformOwnerActions(Method method, String path, String body) {
        authenticated(member).body(body).request(method, resolve(path))
                .then().statusCode(403)
                .body("detail", equalTo("Only the group owner can do this"));

        assertGroupUntouched();
    }

    private String resolve(String path) {
        return path.replace("{groupId}", groupId.toString())
                .replace("{member}", memberId.toString())
                .replace("{owner}", ownerId.toString())
                .replace("{expense}", expenseId.toString());
    }

    private String resolveBody(String body) {
        return BODY_EXPENSE.equals(body) ? expenseBody(ownerId, 0) : body;
    }

    private void assertGroupUntouched() {
        authenticated(owner).get("/api/groups/{id}", groupId)
                .then().statusCode(200)
                .body("name", equalTo("Private"))
                .body("members.size()", equalTo(2));
        authenticated(owner).get("/api/groups/{g}/expenses", groupId)
                .then().statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].version", nullValue());
        authenticated(owner).get("/api/groups/{g}/expenses/{e}", groupId, expenseId)
                .then().statusCode(200)
                .body("version", equalTo(0));
    }
}
