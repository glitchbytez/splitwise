package com.glitchbytez.splitwise.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.glitchbytez.splitwise.expense.split.SplitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of create (POST) and full replace (PUT). All amounts are USD with at most two decimals. */
public record ExpenseRequest(
        @NotBlank @Size(max = 200) String description,
        @Schema(type = "string", example = "90.00")
        @NotNull @DecimalMin("0.01") @DecimalMax(ExpenseRequest.MAX_AMOUNT) @Digits(integer = 9, fraction = 2)
        BigDecimal amount,
        @NotNull LocalDate date,
        @NotNull UUID paidBy,
        ExpenseCategory category,
        @Size(max = 1000) String notes,
        @NotNull SplitType splitType,
        @NotEmpty @Size(max = ExpenseRequest.MAX_PARTICIPANTS) List<@NotNull @Valid ParticipantRequest> participants,
        @Schema(description = "Required on PUT: the version you last read, to detect concurrent edits")
        Long version) {

    /** Keeps every derived sum (percent arithmetic, balances across many expenses) far away from overflow. */
    static final String MAX_AMOUNT = "100000000.00";
    static final int MAX_PARTICIPANTS = 100;
}
