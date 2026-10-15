package com.glitchbytez.splitwise.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class PasswordConfig {

    /** Spec section 7: BCrypt strength 10-12. 12 costs about 250 ms per hash on current hardware. */
    static final int BCRYPT_STRENGTH = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
