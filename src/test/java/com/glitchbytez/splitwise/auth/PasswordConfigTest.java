package com.glitchbytez.splitwise.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PasswordConfig.class);

    @Test
    void usesBcryptStrength12ByDefault() {
        context.run(ctx -> assertThat(ctx.getBean(PasswordEncoder.class).encode("password")).startsWith("$2a$12$"));
    }

    @Test
    void strengthIsConfigurable() {
        context.withPropertyValues("app.security.bcrypt-strength=10")
                .run(ctx -> assertThat(ctx.getBean(PasswordEncoder.class).encode("password")).startsWith("$2a$10$"));
    }
}
