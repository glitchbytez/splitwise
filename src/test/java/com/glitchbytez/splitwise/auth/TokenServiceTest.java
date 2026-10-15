package com.glitchbytez.splitwise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class TokenServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final JwtProperties PROPERTIES =
            new JwtProperties("unit-test-secret-that-is-long-enough-for-hs256", "splitwise", TTL);

    private final JwtConfig config = new JwtConfig();
    private final JwtDecoder decoder = decoderFor(PROPERTIES);

    @Test
    void issuesATokenThatTheDecoderAccepts() {
        UUID userId = UUID.randomUUID();

        TokenResponse response = tokenService(Clock.systemUTC()).issueAccessToken(userId);
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(TTL.toSeconds());
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("splitwise");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(TTL);
    }

    @Test
    void containsNoPersonalDataBeyondTheUserId() {
        Jwt jwt = decoder.decode(tokenService(Clock.systemUTC()).issueAccessToken(UUID.randomUUID()).accessToken());

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti");
    }

    @Test
    void rejectsAnExpiredToken() {
        // Issued long enough ago to be past expiry plus the decoder's 60-second clock skew allowance.
        Clock past = Clock.fixed(Instant.now().minus(TTL).minusSeconds(120), ZoneOffset.UTC);
        String token = tokenService(past).issueAccessToken(UUID.randomUUID()).accessToken();

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        var otherProperties = new JwtProperties("a-completely-different-secret-of-enough-length", "splitwise", TTL);
        String foreignToken = new TokenService(
                config.jwtEncoder(config.jwtSigningKey(otherProperties)), otherProperties, Clock.systemUTC())
                .issueAccessToken(UUID.randomUUID()).accessToken();

        assertThatThrownBy(() -> decoder.decode(foreignToken)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        var otherIssuer = new JwtProperties(PROPERTIES.secret(), "someone-else", TTL);
        String token = new TokenService(config.jwtEncoder(config.jwtSigningKey(otherIssuer)), otherIssuer,
                Clock.systemUTC()).issueAccessToken(UUID.randomUUID()).accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsATamperedToken() {
        String token = tokenService(Clock.systemUTC()).issueAccessToken(UUID.randomUUID()).accessToken();
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"iss\":\"splitwise\"}").getBytes());

        assertThatThrownBy(() -> decoder.decode(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void responseToStringDoesNotExposeTheToken() {
        TokenResponse response = tokenService(Clock.systemUTC()).issueAccessToken(UUID.randomUUID());

        assertThat(response.toString()).doesNotContain(response.accessToken());
    }

    private TokenService tokenService(Clock clock) {
        return new TokenService(config.jwtEncoder(config.jwtSigningKey(PROPERTIES)), PROPERTIES, clock);
    }

    private JwtDecoder decoderFor(JwtProperties properties) {
        return config.jwtDecoder(config.jwtSigningKey(properties), properties);
    }
}
