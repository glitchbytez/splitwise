package com.glitchbytez.splitwise.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived access tokens. The only identifying claim is the user id ({@code sub});
 * no email or name is put in the token (data minimisation, and tokens are readable by anyone holding them).
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issueAccessToken(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(JwtConfig.ALGORITHM).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return TokenResponse.bearer(token, properties.accessTokenTtl());
    }
}
