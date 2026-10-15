package com.glitchbytez.splitwise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.glitchbytez.splitwise.common.error.BadRequestException;
import com.glitchbytez.splitwise.common.error.ConflictException;
import com.glitchbytez.splitwise.common.error.UnauthorizedException;
import com.glitchbytez.splitwise.user.User;
import com.glitchbytez.splitwise.user.UserRepository;
import com.glitchbytez.splitwise.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_HASH = "$2a$12$dummy";

    @Mock
    UserRepository users;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    TokenService tokenService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(users, passwordEncoder, tokenService);
    }

    @Test
    void registerNormalisesEmailTrimsNameAndStoresOnlyTheHash() {
        when(passwordEncoder.encode("correct horse")).thenReturn("$2a$12$hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(
                new RegisterRequest("  Alice@Example.COM ", "  Alice  ", "correct horse"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Alice");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyTakenRegardlessOfCase() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("ALICE@example.com", "A", "password1")))
                .isInstanceOf(ConflictException.class)
                .hasMessage(AuthService.EMAIL_TAKEN);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerTurnsAUniqueConstraintRaceIntoAConflict() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("a@example.com", "A", "password1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerRejectsPasswordsLongerThan72BytesEvenIfUnder72Characters() {
        String password = "é".repeat(40); // 40 characters, 80 bytes in UTF-8

        assertThatThrownBy(() -> authService.register(new RegisterRequest("a@example.com", "A", password)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("72 bytes");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void loginIssuesATokenForValidCredentials() {
        User alice = persistedUser("alice@example.com", "$2a$12$alice");
        when(users.findByEmailAndDeletedAtIsNull("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("secret-pass", "$2a$12$alice")).thenReturn(true);
        TokenResponse token = new TokenResponse("jwt", "Bearer", 1800);
        when(tokenService.issueAccessToken(alice.getId())).thenReturn(token);

        assertThat(authService.login(new LoginRequest(" Alice@Example.com", "secret-pass"))).isEqualTo(token);
    }

    @Test
    void loginRejectsAWrongPassword() {
        User alice = persistedUser("alice@example.com", "$2a$12$alice");
        when(users.findByEmailAndDeletedAtIsNull("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("wrong", "$2a$12$alice")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        verify(tokenService, never()).issueAccessToken(any());
    }

    @Test
    void loginRejectsAnUnknownEmailWithTheSameMessageAndStillChecksAPassword() {
        when(users.findByEmailAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        // A hash comparison still happens so response time doesn't reveal whether the account exists.
        verify(passwordEncoder).matches(eq("whatever"), eq(DUMMY_HASH));
    }

    @Test
    void loginRejectsOverlongPasswordsWithoutHashing() {
        User alice = persistedUser("alice@example.com", "$2a$12$alice");
        when(users.findByEmailAndDeletedAtIsNull("alice@example.com")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "x".repeat(73))))
                .isInstanceOf(UnauthorizedException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    private static User persistedUser(String email, String hash) {
        User user = User.register(email, "Name", hash);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
