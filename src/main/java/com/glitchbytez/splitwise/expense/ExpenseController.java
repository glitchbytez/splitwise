package com.glitchbytez.splitwise.expense;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import com.glitchbytez.splitwise.auth.CurrentUserId;
import com.glitchbytez.splitwise.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@Tag(name = "Expenses", description = "Shared expenses within a group (USD)")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
@ApiResponse(responseCode = "403", description = "Not a member of this group")
@ApiResponse(responseCode = "404", description = "Group (or expense) not found")
class ExpenseController {

    private final ExpenseService expenseService;

    ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @Operation(summary = "Add an expense",
            description = "EQUAL: participants carry only userId. EXACT: each has an amount; amounts must sum to the "
                    + "total. PERCENTAGE: each has a percentage; percentages must sum to exactly 100.00.")
    @ApiResponse(responseCode = "201", description = "Expense created")
    @ApiResponse(responseCode = "400", description = "Invalid input, inconsistent split, or a payer/participant "
            + "who is not a member")
    ResponseEntity<ExpenseResponse> create(@CurrentUserId UUID userId, @PathVariable UUID groupId,
                                           @Valid @RequestBody ExpenseRequest request) {
        ExpenseResponse expense = expenseService.create(groupId, userId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + groupId + "/expenses/" + expense.id()))
                .body(expense);
    }

    @GetMapping
    @Operation(summary = "List expenses",
            description = "Filters combine with AND. Sort by `date` (default, newest first), `amount` or `createdAt`.")
    PageResponse<ExpenseSummaryResponse> list(
            @CurrentUserId UUID userId,
            @PathVariable UUID groupId,
            @Parameter(description = "Only expenses paid by this user") @RequestParam(required = false) UUID paidBy,
            @RequestParam(required = false) ExpenseCategory category,
            @Parameter(description = "Inclusive start date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Inclusive end date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Case-insensitive text search in the description")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return expenseService.list(groupId, userId, new ExpenseFilter(paidBy, category, from, to, q), pageable);
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get an expense with its shares")
    ExpenseResponse get(@CurrentUserId UUID userId, @PathVariable UUID groupId, @PathVariable UUID expenseId) {
        return expenseService.get(groupId, expenseId, userId);
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Replace an expense", description = "Send the full expense plus the `version` you last read.")
    @ApiResponse(responseCode = "400", description = "Invalid input or missing version")
    @ApiResponse(responseCode = "409", description = "The expense was changed by someone else since you read it")
    ExpenseResponse update(@CurrentUserId UUID userId, @PathVariable UUID groupId, @PathVariable UUID expenseId,
                           @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(groupId, expenseId, userId, request);
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense (soft delete; it stops counting towards balances)")
    @ApiResponse(responseCode = "204", description = "Expense deleted")
    void delete(@CurrentUserId UUID userId, @PathVariable UUID groupId, @PathVariable UUID expenseId) {
        expenseService.delete(groupId, expenseId, userId);
    }
}
