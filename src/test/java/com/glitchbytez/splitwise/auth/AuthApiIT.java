package com.glitchbytez.splitwise.auth;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthApiIT extends ApiIntegrationTest {

    @Nested
    class Register {

        @Test
        void createsAnAccountAndReturnsThePublicProfile() {
            json()
                    .body(Map.of("email", "Alice@Example.com", "displayName", "Alice", "password", DEFAULT_PASSWORD))
                    .post("/api/auth/register")
                    .then().statusCode(201)
                    .header("Location", "/api/users/me")
                    .body("id", matchesPattern("[0-9a-f-]{36}"))
                    .body("email", equalTo("alice@example.com"))
                    .body("displayName", equalTo("Alice"))
                    .body("createdAt", notNullValue())
                    .body("$", not(hasKey("password")))
                    .body("$", not(hasKey("passwordHash")));
        }

        @Test
        void storesABcryptHashNotThePassword() {
            register("alice@example.com", "Alice", DEFAULT_PASSWORD);

            String hash = jdbc.queryForObject(
                    "select password_hash from users where email = 'alice@example.com'", String.class);
            assertThat(hash).startsWith("$2a$12$").doesNotContain(DEFAULT_PASSWORD);
        }

        @Test
        void rejectsADuplicateEmailCaseInsensitively() {
            register("alice@example.com", "Alice", DEFAULT_PASSWORD);

            json()
                    .body(Map.of("email", "ALICE@example.COM", "displayName", "Imposter", "password", DEFAULT_PASSWORD))
                    .post("/api/auth/register")
                    .then().statusCode(409)
                    .contentType("application/problem+json")
                    .body("detail", equalTo("An account with this email already exists"));
        }

        @Test
        void reportsEveryInvalidField() {
            json()
                    .body(Map.of("email", "not-an-email", "displayName", " ", "password", "short"))
                    .post("/api/auth/register")
                    .then().statusCode(400)
                    .contentType("application/problem+json")
                    .body("errors.field", containsInAnyOrder("email", "displayName", "password"));
        }

        @Test
        void rejectsMissingFields() {
            json()
                    .body(Map.of())
                    .post("/api/auth/register")
                    .then().statusCode(400)
                    .body("errors.field", containsInAnyOrder("email", "displayName", "password"));
        }

        @Test
        void rejectsMalformedJson() {
            json()
                    .body("{\"email\": ")
                    .post("/api/auth/register")
                    .then().statusCode(400)
                    .contentType("application/problem+json");
        }
    }

    @Nested
    class Login {

        @Test
        void returnsABearerTokenForValidCredentials() {
            register("alice@example.com", "Alice", DEFAULT_PASSWORD);

            json()
                    .body(Map.of("email", "alice@example.com", "password", DEFAULT_PASSWORD))
                    .post("/api/auth/login")
                    .then().statusCode(200)
                    .body("accessToken", matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+"))
                    .body("tokenType", equalTo("Bearer"))
                    .body("expiresIn", equalTo(1800));
        }

        @Test
        void acceptsTheEmailInAnyCase() {
            register("alice@example.com", "Alice", DEFAULT_PASSWORD);

            String token = login("  ALICE@example.com ", DEFAULT_PASSWORD);

            authenticated(token).get("/api/users/me").then().statusCode(200);
        }

        @Test
        void rejectsAWrongPassword() {
            register("alice@example.com", "Alice", DEFAULT_PASSWORD);

            json()
                    .body(Map.of("email", "alice@example.com", "password", "wrong-password"))
                    .post("/api/auth/login")
                    .then().statusCode(401)
                    .contentType("application/problem+json")
                    .body("detail", equalTo("Invalid email or password"));
        }

        @Test
        void rejectsAnUnknownEmailWithTheSameResponseAsAWrongPassword() {
            json()
                    .body(Map.of("email", "nobody@example.com", "password", DEFAULT_PASSWORD))
                    .post("/api/auth/login")
                    .then().statusCode(401)
                    .body("detail", equalTo("Invalid email or password"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"email\": \"\", \"password\": \"x\"}", "{\"email\": \"a@b.c\"}", "{}"})
        void rejectsIncompleteRequests(String body) {
            json()
                    .body(body)
                    .post("/api/auth/login")
                    .then().statusCode(400)
                    .contentType("application/problem+json");
        }
    }
}
