package com.glitchbytez.splitwise;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.glitchbytez.splitwise.support.TestcontainersConfiguration;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Boots the whole application on a random port against PostgreSQL and checks the Phase 0 surface. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApplicationIT {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void healthEndpointIsPublicAndUp() {
        given()
                .when().get("/actuator/health")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body(not(containsString("components")));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        given()
                .when().get("/actuator/env")
                .then().statusCode(401);
    }

    @Test
    void swaggerUiLoads() {
        given()
                .when().get("/swagger-ui.html")
                .then().statusCode(200)
                .contentType(containsString("text/html"));
    }

    @Test
    void openApiDocumentIsServedWithBearerAuthScheme() {
        given()
                .when().get("/v3/api-docs")
                .then().statusCode(200)
                .body("info.title", equalTo("Splitwise API"))
                .body("components.securitySchemes.bearerAuth.scheme", equalTo("bearer"));
    }

    @Test
    void apiRequiresAuthenticationAndAnswersWithProblemDetail() {
        given()
                .when().get("/api/groups")
                .then().statusCode(401)
                .contentType(containsString("application/problem+json"))
                .body("status", equalTo(401))
                .body("title", equalTo("Unauthorized"))
                .body("detail", equalTo("Authentication is required to access this resource"));
    }

    @Test
    void noSessionCookieIsIssued() {
        given()
                .when().get("/api/groups")
                .then().header("Set-Cookie", nullValue());
    }
}
