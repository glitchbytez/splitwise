package com.glitchbytez.splitwise.group;

import java.util.UUID;

import com.glitchbytez.splitwise.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/members")
@Tag(name = "Group members")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
@ApiResponse(responseCode = "404", description = "Group not found")
class GroupMemberController {

    private final GroupMemberService memberService;

    GroupMemberController(GroupMemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a registered user to the group by email",
            description = "Any member can add people. The email must belong to a registered user (no invitations).")
    @ApiResponse(responseCode = "201", description = "Member added")
    @ApiResponse(responseCode = "403", description = "Not a member of this group")
    @ApiResponse(responseCode = "404", description = "Group not found, or no user has this email")
    @ApiResponse(responseCode = "409", description = "Already a member")
    MemberResponse add(@CurrentUserId UUID userId, @PathVariable UUID groupId,
                       @Valid @RequestBody AddMemberRequest request) {
        return memberService.add(groupId, userId, request);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member (owner only), or leave the group by passing your own user id",
            description = "Only allowed when the member's balance in the group is zero. The owner cannot leave.")
    @ApiResponse(responseCode = "204", description = "Member removed / left")
    @ApiResponse(responseCode = "403", description = "Not a member, or not the owner when removing someone else")
    @ApiResponse(responseCode = "409", description = "Non-zero balance, or the owner trying to leave")
    void remove(@CurrentUserId UUID userId, @PathVariable UUID groupId, @PathVariable UUID memberId) {
        memberService.remove(groupId, userId, memberId);
    }
}
