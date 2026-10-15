package com.glitchbytez.splitwise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupMemberApiIT extends ApiIntegrationTest {

    String owner;
    String bob;
    UUID ownerId;
    UUID bobId;
    UUID groupId;

    @BeforeEach
    void setUp() {
        owner = registerAndLogin("owner@example.com");
        bob = registerAndLogin("bob@example.com");
        ownerId = userIdOf(owner);
        bobId = userIdOf(bob);
        groupId = createGroup(owner, "Flat");
    }

    @Test
    void addsARegisteredUserByEmailCaseInsensitively() {
        authenticated(owner).body(Map.of("email", "BOB@Example.com"))
                .post("/api/groups/{id}/members", groupId)
                .then().statusCode(201)
                .body("userId", equalTo(bobId.toString()))
                .body("displayName", equalTo("Test User"))
                .body("role", equalTo("MEMBER"));

        authenticated(bob).get("/api/groups/{id}", groupId)
                .then().statusCode(200)
                .body("myRole", equalTo("MEMBER"))
                .body("members.userId", contains(ownerId.toString(), bobId.toString()));
    }

    @Test
    void rejectsAnEmailWithNoAccount() {
        authenticated(owner).body(Map.of("email", "nobody@example.com"))
                .post("/api/groups/{id}/members", groupId)
                .then().statusCode(404)
                .body("detail", equalTo("No registered user has this email"));
    }

    @Test
    void rejectsAnEmailOfADeletedAccount() {
        authenticated(bob).delete("/api/users/me").then().statusCode(204);

        authenticated(owner).body(Map.of("email", "bob@example.com"))
                .post("/api/groups/{id}/members", groupId)
                .then().statusCode(404);
    }

    @Test
    void rejectsAddingAnExistingMember() {
        addMember(owner, groupId, "bob@example.com");

        authenticated(owner).body(Map.of("email", "bob@example.com"))
                .post("/api/groups/{id}/members", groupId)
                .then().statusCode(409)
                .body("detail", equalTo("This user is already a member of the group"));
        authenticated(owner).body(Map.of("email", "owner@example.com"))
                .post("/api/groups/{id}/members", groupId)
                .then().statusCode(409);
    }

    @Test
    void anyMemberCanAddPeople() {
        registerAndLogin("carol@example.com");
        addMember(owner, groupId, "bob@example.com");

        addMember(bob, groupId, "carol@example.com");

        authenticated(owner).get("/api/groups/{id}", groupId).then().body("members", hasSize(3));
    }

    @Test
    void validatesTheEmail() {
        authenticated(owner).body(Map.of("email", "not-an-email"))
                .post("/api/groups/{id}/members", groupId)
                .then().statusCode(400)
                .body("errors[0].field", equalTo("email"));
    }

    @Test
    void ownerCanRemoveAMemberWhoThenLosesAccess() {
        addMember(owner, groupId, "bob@example.com");

        authenticated(owner).delete("/api/groups/{id}/members/{uid}", groupId, bobId).then().statusCode(204);

        authenticated(bob).get("/api/groups/{id}", groupId).then().statusCode(403);
        authenticated(owner).get("/api/groups/{id}", groupId).then().body("members", hasSize(1));
    }

    @Test
    void aMemberCanLeave() {
        addMember(owner, groupId, "bob@example.com");

        authenticated(bob).delete("/api/groups/{id}/members/{uid}", groupId, bobId).then().statusCode(204);

        authenticated(bob).get("/api/groups/{id}", groupId).then().statusCode(403);
    }

    @Test
    void theOwnerCannotLeave() {
        authenticated(owner).delete("/api/groups/{id}/members/{uid}", groupId, ownerId)
                .then().statusCode(409)
                .body("detail", equalTo("The owner cannot leave the group; delete the group instead"));
    }

    @Test
    void removingSomeoneWhoIsNotAMemberIsNotFound() {
        authenticated(owner).delete("/api/groups/{id}/members/{uid}", groupId, bobId).then().statusCode(404);
    }

    @Test
    void aFormerMemberCanBeAddedBack() {
        addMember(owner, groupId, "bob@example.com");
        authenticated(bob).delete("/api/groups/{id}/members/{uid}", groupId, bobId).then().statusCode(204);

        addMember(owner, groupId, "bob@example.com");

        authenticated(bob).get("/api/groups/{id}", groupId).then().statusCode(200).body("myRole", equalTo("MEMBER"));
        assertThat(jdbc.queryForObject("select count(*) from group_members where group_id = ?",
                Integer.class, groupId)).as("the membership row is reactivated, not duplicated").isEqualTo(2);
    }

    @Test
    void membershipChangesAreLoggedWithoutPersonalData() {
        addMember(owner, groupId, "bob@example.com");
        authenticated(bob).delete("/api/groups/{id}/members/{uid}", groupId, bobId).then().statusCode(204);
        addMember(owner, groupId, "bob@example.com");
        authenticated(owner).delete("/api/groups/{id}/members/{uid}", groupId, bobId).then().statusCode(204);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select type, actor_id, entity_id, payload::text as payload from activity_log
                where group_id = ? order by created_at, type
                """, groupId);

        assertThat(rows).extracting(r -> r.get("type")).containsExactly(
                "GROUP_CREATED", "MEMBER_ADDED", "MEMBER_LEFT", "MEMBER_ADDED", "MEMBER_REMOVED");
        assertThat(rows.get(2)).containsEntry("actor_id", bobId).containsEntry("entity_id", bobId);
        assertThat(rows.get(4)).containsEntry("actor_id", ownerId).containsEntry("entity_id", bobId);
        assertThat(rows).extracting(r -> r.get("payload").toString()).noneMatch(p -> p.contains("@"));
    }
}
