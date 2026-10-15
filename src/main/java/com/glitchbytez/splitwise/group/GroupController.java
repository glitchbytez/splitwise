package com.glitchbytez.splitwise.group;

import java.net.URI;
import java.util.UUID;

import com.glitchbytez.splitwise.auth.CurrentUserId;
import com.glitchbytez.splitwise.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "Groups the caller belongs to")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
class GroupController {

    private final GroupService groupService;

    GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @Operation(summary = "Create a group; the caller becomes its owner")
    @ApiResponse(responseCode = "201", description = "Group created")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    ResponseEntity<GroupResponse> create(@CurrentUserId UUID userId, @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse group = groupService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + group.id())).body(group);
    }

    @GetMapping
    @Operation(summary = "List my groups", description = "Sortable by `name` or `createdAt` (default: newest first).")
    PageResponse<GroupSummaryResponse> list(
            @CurrentUserId UUID userId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return groupService.listForMember(userId, pageable);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get a group with its members")
    @ApiResponse(responseCode = "403", description = "Not a member of this group")
    @ApiResponse(responseCode = "404", description = "Group not found")
    GroupResponse get(@CurrentUserId UUID userId, @PathVariable UUID groupId) {
        return groupService.get(groupId, userId);
    }

    @PatchMapping("/{groupId}")
    @Operation(summary = "Rename a group or change its description (owner only)")
    @ApiResponse(responseCode = "403", description = "Not the owner of this group")
    @ApiResponse(responseCode = "404", description = "Group not found")
    GroupResponse update(@CurrentUserId UUID userId, @PathVariable UUID groupId,
                         @Valid @RequestBody UpdateGroupRequest request) {
        return groupService.update(groupId, userId, request);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a group (owner only)")
    @ApiResponse(responseCode = "204", description = "Group deleted")
    @ApiResponse(responseCode = "403", description = "Not the owner of this group")
    @ApiResponse(responseCode = "404", description = "Group not found")
    void delete(@CurrentUserId UUID userId, @PathVariable UUID groupId) {
        groupService.delete(groupId, userId);
    }
}
