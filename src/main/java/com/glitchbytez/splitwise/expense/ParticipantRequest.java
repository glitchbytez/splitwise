package com.glitchbytez.splitwise.expense;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * A participant of an expense. EQUAL: only {@code userId}. EXACT: {@code amount}. PERCENTAGE: {@code percentage}.
 */
public record ParticipantRequest(
        @NotNull UUID userId,
        @Schema(type = "string", example = "30.00", description = "EXACT splits only")
        @DecimalMin("0.00") @Digits(integer = 9, fraction = 2) BigDecimal amount,
        @Schema(type = "string", example = "33.33", description = "PERCENTAGE splits only")
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal percentage) {
}
