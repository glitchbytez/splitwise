package com.glitchbytez.splitwise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void acceptsASecretOfAtLeast32Bytes() {
        var properties = new JwtProperties("x".repeat(32), "splitwise", Duration.ofMinutes(30));

        assertThat(properties.secretBytes()).hasSize(32);
    }

    @Test
    void rejectsASecretTooShortForHs256() {
        assertThatThrownBy(() -> new JwtProperties("x".repeat(31), "splitwise", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void doesNotLeakTheSecretInItsErrorMessage() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", "splitwise", Duration.ofMinutes(30)))
                .message().doesNotContain("short-secret");
    }
}
