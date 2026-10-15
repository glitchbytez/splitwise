package com.glitchbytez.splitwise.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundBecomes404ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Group not found"))
                .andExpect(jsonPath("$.instance").value("/test/not-found"));
    }

    @Test
    void conflictBecomes409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void forbiddenBecomes403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the owner can do that"));
    }

    @Test
    void badRequestBecomes400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Shares must sum to the total"));
    }

    @Test
    void validationErrorsListEveryInvalidFieldInStableOrder() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" \", \"quantity\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[1].field").value("quantity"))
                .andExpect(jsonPath("$.errors[1].message").value("must be greater than 0"));
    }

    @Test
    void malformedJsonBecomes400ProblemDetail() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void authenticationFailureBecomes401() throws Exception {
        mockMvc.perform(get("/test/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));
    }

    @Test
    void accessDeniedBecomes403() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource"));
    }

    @Test
    void optimisticLockFailureBecomes409() throws Exception {
        mockMvc.perform(get("/test/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The resource was modified by someone else; reload and try again"));
    }

    @Test
    void unexpectedErrorsBecome500WithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret-connection-string"))));
    }

    record ValidatedRequest(@NotBlank String name, @Positive int quantity) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("Group not found");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("Email already registered");
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException("Only the owner can do that");
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new BadRequestException("Shares must sum to the total");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody ValidatedRequest request) {
        }

        @GetMapping("/test/unauthenticated")
        void unauthenticated() {
            throw new InsufficientAuthenticationException("no token");
        }

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/test/stale")
        void stale() {
            throw new ObjectOptimisticLockingFailureException(Object.class, "id");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret-connection-string");
        }
    }
}
