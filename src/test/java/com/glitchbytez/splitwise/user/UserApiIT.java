package com.glitchbytez.splitwise.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import java.util.Map;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

class UserApiIT extends ApiIntegrationTest {

    @Test
    void returnsTheCallersOwnProfile() {
        register("alice@example.com", "Alice", DEFAULT_PASSWORD);
        String token = login("alice@example.com", DEFAULT_PASSWORD);

        authenticated(token).get("/api/users/me")
                .then().statusCode(200)
                .body("email", equalTo("alice@example.com"))
                .body("displayName", equalTo("Alice"))
                .body("$", not(hasKey("passwordHash")));
    }

    @Test
    void eachTokenOnlyGivesAccessToItsOwnUser() {
        String aliceToken = registerAndLogin("alice@example.com");
        String bobToken = registerAndLogin("bob@example.com");

        authenticated(aliceToken).get("/api/users/me").then().body("email", equalTo("alice@example.com"));
        authenticated(bobToken).get("/api/users/me").then().body("email", equalTo("bob@example.com"));
    }

    @Test
    void updatesTheDisplayName() {
        String token = registerAndLogin("alice@example.com");

        authenticated(token).body(Map.of("displayName", "  Ally  ")).patch("/api/users/me")
                .then().statusCode(200)
                .body("displayName", equalTo("Ally"));

        authenticated(token).get("/api/users/me").then().body("displayName", equalTo("Ally"));
    }

    @Test
    void rejectsABlankDisplayName() {
        String token = registerAndLogin("alice@example.com");

        authenticated(token).body(Map.of("displayName", "")).patch("/api/users/me")
                .then().statusCode(400)
                .body("errors[0].field", equalTo("displayName"));
    }

    @Test
    void deletingTheAccountAnonymisesItAndBlocksLogin() {
        String token = registerAndLogin("alice@example.com");

        authenticated(token).delete("/api/users/me").then().statusCode(204);

        Map<String, Object> row = jdbc.queryForMap(
                "select email, display_name, password_hash, deleted_at from users");
        assertThat((String) row.get("email")).endsWith("@deleted.invalid").doesNotContain("alice");
        assertThat(row.get("display_name")).isEqualTo("Deleted user");
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();

        json().body(Map.of("email", "alice@example.com", "password", DEFAULT_PASSWORD))
                .post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void aTokenOfADeletedAccountNoLongerReachesTheProfile() {
        String token = registerAndLogin("alice@example.com");
        authenticated(token).delete("/api/users/me").then().statusCode(204);

        authenticated(token).get("/api/users/me").then().statusCode(404);
        authenticated(token).delete("/api/users/me").then().statusCode(404);
    }

    @Test
    void theEmailOfADeletedAccountCanBeRegisteredAgain() {
        String token = registerAndLogin("alice@example.com");
        authenticated(token).delete("/api/users/me").then().statusCode(204);

        register("alice@example.com", "New Alice", DEFAULT_PASSWORD);

        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(2);
    }
}
