package com.glitchbytez.splitwise.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.glitchbytez.splitwise.common.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    UserRepository users;

    UserService userService;
    User alice;

    @BeforeEach
    void setUp() {
        userService = new UserService(users, Clock.fixed(NOW, ZoneOffset.UTC));
        alice = User.register("alice@example.com", "Alice", "$2a$12$hash");
        ReflectionTestUtils.setField(alice, "id", UUID.randomUUID());
    }

    @Test
    void returnsTheProfileOfAnActiveUser() {
        when(users.findByIdAndDeletedAtIsNull(alice.getId())).thenReturn(Optional.of(alice));

        UserResponse profile = userService.getProfile(alice.getId());

        assertThat(profile.id()).isEqualTo(alice.getId());
        assertThat(profile.email()).isEqualTo("alice@example.com");
        assertThat(profile.displayName()).isEqualTo("Alice");
    }

    @Test
    void reportsNotFoundForADeletedOrUnknownUser() {
        UUID id = UUID.randomUUID();
        when(users.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesTheTrimmedDisplayName() {
        when(users.findByIdAndDeletedAtIsNull(alice.getId())).thenReturn(Optional.of(alice));

        UserResponse updated = userService.updateProfile(alice.getId(), new UpdateUserRequest("  Ally  "));

        assertThat(updated.displayName()).isEqualTo("Ally");
        assertThat(alice.getDisplayName()).isEqualTo("Ally");
    }

    @Test
    void deletingAnAccountAnonymisesItButKeepsTheRow() {
        when(users.findByIdAndDeletedAtIsNull(alice.getId())).thenReturn(Optional.of(alice));

        userService.deleteAccount(alice.getId());

        assertThat(alice.getEmail()).isEqualTo("deleted-" + alice.getId() + "@deleted.invalid");
        assertThat(alice.getDisplayName()).isEqualTo(User.DELETED_DISPLAY_NAME);
        assertThat(alice.getPasswordHash()).isNull();
        assertThat(alice.getDeletedAt()).isEqualTo(NOW);
        assertThat(alice.isDeleted()).isTrue();
    }

    @Test
    void cannotDeleteAnAlreadyDeletedAccount() {
        when(users.findByIdAndDeletedAtIsNull(alice.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount(alice.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void entityToStringDoesNotExposePersonalData() {
        assertThat(alice.toString()).doesNotContain("alice@example.com", "Alice", "$2a$12$hash");
    }

    @Test
    void emailsAreNormalisedToTrimmedLowerCase() {
        assertThat(EmailAddresses.normalize("  Bob.Smith@Example.COM ")).isEqualTo("bob.smith@example.com");
    }
}
