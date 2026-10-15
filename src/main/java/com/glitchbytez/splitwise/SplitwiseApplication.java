package com.glitchbytez.splitwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// The default in-memory user (with a generated password logged at startup) is never wanted:
// authentication is JWT-based and users live in the database.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SplitwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SplitwiseApplication.class, args);
    }

}
