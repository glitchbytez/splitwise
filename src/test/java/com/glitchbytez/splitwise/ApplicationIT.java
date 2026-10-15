package com.glitchbytez.splitwise;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

/** Checks the application-wide surface: health, API docs and the default-deny security posture. */
class ApplicationIT extends ApiIntegrationTest {

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
    void openApiDocumentsAuthEndpointsAsPublicAndHidesTheInjectedUserId() {
        given()
                .when().get("/v3/api-docs")
                .then().statusCode(200)
                .body("paths.'/api/auth/login'.post.security", equalTo(List.of()))
                .body("paths.'/api/users/me'.get.parameters", nullValue());
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
