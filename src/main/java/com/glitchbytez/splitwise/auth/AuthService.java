package com.glitchbytez.splitwise.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.glitchbytez.splitwise.common.error.BadRequestException;
import com.glitchbytez.splitwise.common.error.ConflictException;
import com.glitchbytez.splitwise.common.error.UnauthorizedException;
import com.glitchbytez.splitwise.user.EmailAddresses;
import com.glitchbytez.splitwise.user.User;
import com.glitchbytez.splitwise.user.UserRepository;
import com.glitchbytez.splitwise.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes of a password; longer input is rejected rather than silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    static final String EMAIL_TAKEN = "An account with this email already exists";
    static final String INVALID_CREDENTIALS = "Invalid email or password";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Compared against when the email is unknown, so a failed login takes the same time either way. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new BadRequestException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        String email = EmailAddresses.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        User user = User.register(email, request.displayName().trim(), passwordEncoder.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration for the same email.
            throw new ConflictException(EMAIL_TAKEN);
        }
        log.info("Registered user {}", user.getId());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmailAndDeletedAtIsNull(EmailAddresses.normalize(request.email()));
        boolean passwordMatches = !exceedsBcryptLimit(request.password())
                && passwordEncoder.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyPasswordHash));
        if (user.isEmpty() || !passwordMatches) {
            // Same message for unknown email and wrong password: don't reveal which accounts exist.
            log.debug("Rejected login attempt");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        log.info("User {} logged in", user.get().getId());
        return tokenService.issueAccessToken(user.get().getId());
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
