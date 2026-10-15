package com.glitchbytez.splitwise.support;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for API tests: the full application on a random port, backed by a Testcontainers PostgreSQL that
 * is shared by every subclass (same Spring context), with all data wiped before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetApiTestState() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        jdbc.execute("truncate table users cascade");
    }

    protected static RequestSpecification json() {
        return given().contentType(ContentType.JSON).accept(ContentType.JSON);
    }

    protected static RequestSpecification authenticated(String token) {
        return json().header("Authorization", "Bearer " + token);
    }

    protected static UUID register(String email, String displayName, String password) {
        String id = json()
                .body(Map.of("email", email, "displayName", displayName, "password", password))
                .post("/api/auth/register")
                .then().statusCode(201)
                .extract().path("id");
        return UUID.fromString(id);
    }

    protected static String login(String email, String password) {
        return json()
                .body(Map.of("email", email, "password", password))
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    /** Registers a user with {@link #DEFAULT_PASSWORD} and returns an access token for them. */
    protected static String registerAndLogin(String email) {
        register(email, "Test User", DEFAULT_PASSWORD);
        return login(email, DEFAULT_PASSWORD);
    }

    protected static UUID createGroup(String token, String name) {
        String id = authenticated(token)
                .body(Map.of("name", name))
                .post("/api/groups")
                .then().statusCode(201)
                .extract().path("id");
        return UUID.fromString(id);
    }

    protected static void addMember(String token, UUID groupId, String email) {
        authenticated(token)
                .body(Map.of("email", email))
                .post("/api/groups/{groupId}/members", groupId)
                .then().statusCode(201);
    }

    protected static UUID userIdOf(String token) {
        return UUID.fromString(authenticated(token).get("/api/users/me").then().extract().path("id"));
    }
}
