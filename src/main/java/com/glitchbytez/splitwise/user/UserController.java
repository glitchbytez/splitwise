package com.glitchbytez.splitwise.user;

import java.util.UUID;

import com.glitchbytez.splitwise.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Users", description = "The authenticated user's own account")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get my profile")
    UserResponse me(@CurrentUserId UUID userId) {
        return userService.getProfile(userId);
    }

    @PatchMapping
    @Operation(summary = "Update my display name")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    UserResponse update(@CurrentUserId UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateProfile(userId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my account",
            description = "Anonymises the account (email, name and password are removed) but keeps expense history "
                    + "so other members' balances stay correct.")
    @ApiResponse(responseCode = "204", description = "Account deleted")
    void delete(@CurrentUserId UUID userId) {
        userService.deleteAccount(userId);
    }
}
