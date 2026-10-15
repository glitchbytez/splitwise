package com.glitchbytez.splitwise;

import com.glitchbytez.splitwise.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/** Runs the app against a throwaway Testcontainers PostgreSQL: {@code ./mvnw spring-boot:test-run}. */
public class TestSplitwiseApplication {

    public static void main(String[] args) {
        SpringApplication.from(SplitwiseApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
