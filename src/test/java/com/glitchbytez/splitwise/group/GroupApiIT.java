package com.glitchbytez.splitwise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GroupApiIT extends ApiIntegrationTest {

    String alice;
    UUID aliceId;

    @BeforeEach
    void setUp() {
        alice = registerAndLogin("alice@example.com");
        aliceId = userIdOf(alice);
    }

    @Nested
    class Create {

        @Test
        void createsAGroupWithTheCallerAsOwner() {
            String id = authenticated(alice)
                    .body(Map.of("name", "  Lisbon trip ", "description", "Flights and food"))
                    .post("/api/groups")
                    .then().statusCode(201)
                    .body("name", equalTo("Lisbon trip"))
                    .body("description", equalTo("Flights and food"))
                    .body("createdBy", equalTo(aliceId.toString()))
                    .body("myRole", equalTo("OWNER"))
                    .body("members", hasSize(1))
                    .body("members[0].userId", equalTo(aliceId.toString()))
                    .body("members[0].role", equalTo("OWNER"))
                    .body("members[0]", not(hasKey("email")))
                    .extract().path("id");

            authenticated(alice).get("/api/groups/{id}", id).then().statusCode(200);
        }

        @Test
        void setsTheLocationHeader() {
            String location = authenticated(alice).body(Map.of("name", "Flat"))
                    .post("/api/groups")
                    .then().statusCode(201)
                    .extract().header("Location");

            assertThat(location).matches("/api/groups/[0-9a-f-]{36}");
        }

        @Test
        void validatesTheRequest() {
            authenticated(alice)
                    .body(Map.of("name", " ", "description", "x".repeat(501)))
                    .post("/api/groups")
                    .then().statusCode(400)
                    .body("errors.field", contains("description", "name"));
        }

        @Test
        void writesAnActivityEntry() {
            UUID groupId = createGroup(alice, "Trip");

            List<String> types = jdbc.queryForList(
                    "select type from activity_log where group_id = ?", String.class, groupId);
            assertThat(types).containsExactly("GROUP_CREATED");
        }
    }

    @Nested
    class ListGroups {

        @Test
        void listsOnlyTheCallersGroupsWithRoleAndMemberCount() {
            String bob = registerAndLogin("bob@example.com");
            UUID shared = createGroup(alice, "Shared");
            addMember(alice, shared, "bob@example.com");
            createGroup(bob, "Bob's own");

            authenticated(alice).get("/api/groups")
                    .then().statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].name", equalTo("Shared"))
                    .body("content[0].myRole", equalTo("OWNER"))
                    .body("content[0].memberCount", equalTo(2));

            authenticated(bob).get("/api/groups")
                    .then().statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("content.find { it.name == 'Shared' }.myRole", equalTo("MEMBER"));
        }

        @Test
        void paginatesWithTheStandardEnvelope() {
            createGroup(alice, "A");
            createGroup(alice, "B");
            createGroup(alice, "C");

            authenticated(alice).queryParam("page", 1).queryParam("size", 2).get("/api/groups")
                    .then().statusCode(200)
                    .body("content", hasSize(1))
                    .body("page", equalTo(1))
                    .body("size", equalTo(2))
                    .body("totalElements", equalTo(3));
        }

        @Test
        void defaultsToNewestFirstAndCanSortByName() {
            createGroup(alice, "Bravo");
            createGroup(alice, "Alpha");
            createGroup(alice, "Charlie");

            authenticated(alice).get("/api/groups")
                    .then().body("content.name", contains("Charlie", "Alpha", "Bravo"));
            authenticated(alice).queryParam("sort", "name,asc").get("/api/groups")
                    .then().body("content.name", contains("Alpha", "Bravo", "Charlie"));
        }

        @Test
        void rejectsUnknownSortProperties() {
            authenticated(alice).queryParam("sort", "createdBy").get("/api/groups")
                    .then().statusCode(400)
                    .body("detail", equalTo("Cannot sort by 'createdBy'. Allowed: createdAt, name"));
        }

        @Test
        void capsThePageSize() {
            authenticated(alice).queryParam("size", 1000).get("/api/groups")
                    .then().statusCode(200)
                    .body("size", equalTo(100));
        }

        @Test
        void excludesDeletedGroupsAndGroupsTheCallerLeft() {
            String bob = registerAndLogin("bob@example.com");
            UUID bobId = userIdOf(bob);
            UUID deleted = createGroup(alice, "Deleted");
            addMember(alice, deleted, "bob@example.com");
            UUID left = createGroup(alice, "Left");
            addMember(alice, left, "bob@example.com");

            authenticated(alice).delete("/api/groups/{id}", deleted).then().statusCode(204);
            authenticated(bob).delete("/api/groups/{id}/members/{uid}", left, bobId).then().statusCode(204);

            authenticated(bob).get("/api/groups").then().body("totalElements", equalTo(0));
        }
    }

    @Nested
    class GetUpdateDelete {

        @Test
        void returnsNotFoundForAnUnknownGroupAndBadRequestForAMalformedId() {
            authenticated(alice).get("/api/groups/{id}", UUID.randomUUID())
                    .then().statusCode(404)
                    .body("detail", equalTo("Group not found"));
            authenticated(alice).get("/api/groups/not-a-uuid").then().statusCode(400);
        }

        @Test
        void ownerCanRenameAndDescribe() {
            UUID groupId = createGroup(alice, "Old");

            authenticated(alice).body(Map.of("name", "New", "description", "Updated"))
                    .patch("/api/groups/{id}", groupId)
                    .then().statusCode(200)
                    .body("name", equalTo("New"))
                    .body("description", equalTo("Updated"));

            authenticated(alice).body(Map.of("description", ""))
                    .patch("/api/groups/{id}", groupId)
                    .then().statusCode(200)
                    .body("name", equalTo("New"))
                    .body("description", nullValue());
        }

        @Test
        void anEmptyPatchIsRejected() {
            UUID groupId = createGroup(alice, "Trip");

            authenticated(alice).body(Map.of()).patch("/api/groups/{id}", groupId)
                    .then().statusCode(400)
                    .body("errors.message", hasItem("at least one of name or description must be provided"));
        }

        @Test
        void aBlankNameIsRejected() {
            UUID groupId = createGroup(alice, "Trip");

            authenticated(alice).body(Map.of("name", "   ")).patch("/api/groups/{id}", groupId)
                    .then().statusCode(400);
        }

        @Test
        void ownerCanDeleteAndTheGroupIsGoneForEveryone() {
            String bob = registerAndLogin("bob@example.com");
            UUID groupId = createGroup(alice, "Trip");
            addMember(alice, groupId, "bob@example.com");

            authenticated(alice).delete("/api/groups/{id}", groupId).then().statusCode(204);

            authenticated(alice).get("/api/groups/{id}", groupId).then().statusCode(404);
            authenticated(bob).get("/api/groups/{id}", groupId).then().statusCode(404);
            authenticated(alice).delete("/api/groups/{id}", groupId).then().statusCode(404);
            assertThat(jdbc.queryForObject("select deleted_at is not null from groups where id = ?",
                    Boolean.class, groupId)).as("soft delete keeps the row").isTrue();
        }
    }
}
