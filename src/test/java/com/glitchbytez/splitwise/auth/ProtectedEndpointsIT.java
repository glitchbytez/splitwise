package com.glitchbytez.splitwise.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import com.glitchbytez.splitwise.support.ApiIntegrationTest;
import io.restassured.http.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Phase 1 acceptance: protected endpoints return 401 without a valid token. */
class ProtectedEndpointsIT extends ApiIntegrationTest {

    @Autowired
    JwtEncoder jwtEncoder;

    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of(Method.GET, "/api/users/me"),
                Arguments.of(Method.PATCH, "/api/users/me"),
                Arguments.of(Method.DELETE, "/api/users/me"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedEndpoints")
    void requireAToken(Method method, String path) {
        json().body("{}").request(method, path)
                .then().statusCode(401)
                .header("WWW-Authenticate", startsWith("Bearer"))
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("detail", equalTo("Authentication is required to access this resource"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedEndpoints")
    void rejectAMalformedToken(Method method, String path) {
        authenticated("not.a.jwt").body("{}").request(method, path)
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("invalid_token"))
                .body("detail", equalTo("The access token is invalid or has expired"));
    }

    @Test
    void rejectAnExpiredToken() {
        UUID userId = register("alice@example.com", "Alice", DEFAULT_PASSWORD);
        Instant issued = Instant.now().minusSeconds(3600);
        String expired = sign(JwtClaimsSet.builder()
                .issuer("splitwise").subject(userId.toString())
                .issuedAt(issued).expiresAt(issued.plusSeconds(1800))
                .build());

        authenticated(expired).get("/api/users/me")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("invalid_token"));
    }

    @Test
    void rejectATokenFromAnotherIssuerEvenIfCorrectlySigned() {
        UUID userId = register("alice@example.com", "Alice", DEFAULT_PASSWORD);
        String foreign = sign(JwtClaimsSet.builder()
                .issuer("evil").subject(userId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build());

        authenticated(foreign).get("/api/users/me").then().statusCode(401);
    }

    @Test
    void rejectAnUnsignedToken() {
        String header = base64("{\"alg\":\"none\"}");
        String payload = base64("{\"iss\":\"splitwise\",\"sub\":\"" + UUID.randomUUID() + "\"}");

        authenticated(header + "." + payload + ".").get("/api/users/me").then().statusCode(401);
    }

    @Test
    void rejectAWellSignedTokenWhoseSubjectIsNotAUserId() {
        String token = sign(JwtClaimsSet.builder()
                .issuer("splitwise").subject("admin")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build());

        authenticated(token).get("/api/users/me").then().statusCode(401);
    }

    @Test
    void publicEndpointsStayReachableWithoutAToken() {
        given().get("/actuator/health").then().statusCode(200);
        json().body("{}").post("/api/auth/login").then().statusCode(400);
        json().body("{}").post("/api/auth/register").then().statusCode(400);
    }

    @Test
    void onlyPostIsPublicOnAuthEndpoints() {
        given().get("/api/auth/login").then().statusCode(401);
    }

    private String sign(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
