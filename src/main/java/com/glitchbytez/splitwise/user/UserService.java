package com.glitchbytez.splitwise.user;

import java.time.Clock;
import java.util.UUID;

import com.glitchbytez.splitwise.common.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(requireActive(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateUserRequest request) {
        User user = requireActive(userId);
        user.rename(request.displayName().trim());
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = requireActive(userId);
        user.anonymise(clock.instant());
        log.info("Anonymised user {}", userId);
    }

    /**
     * A token stays valid until it expires even if the account is deleted meanwhile,
     * so every lookup by the caller's id must still check that the user is active.
     */
    User requireActive(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
