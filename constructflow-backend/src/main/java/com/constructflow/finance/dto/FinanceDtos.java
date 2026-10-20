package com.constructflow.finance.dto;

import com.constructflow.finance.entity.ExpenseApproval;
import com.constructflow.finance.entity.ExpenseStatus;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record BudgetCategoryResponse(UUID id, String name, boolean active) {
    }

    public record CreateBudgetCategoryRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record AllocationItem(@NotNull UUID categoryId,
                                 @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal amount) {
    }

    public record ReplaceAllocationsRequest(@NotNull @Valid List<AllocationItem> allocations) {
    }

    public record AllocationResponse(UUID categoryId, String categoryName, BigDecimal amount) {
    }

    public record BudgetAllocationsResponse(UUID projectId, BigDecimal approvedBudget, BigDecimal totalAllocated,
                                            BigDecimal unallocated, List<AllocationResponse> allocations) {
    }

    public record ProjectRef(UUID id, String code, String name) {
    }

    public record CategoryRef(UUID id, String name) {
    }

    public record CreateExpenseRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 160) String vendorName,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero")
            @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places") BigDecimal amount,
            @NotNull @PastOrPresent LocalDate expenseDate) {
    }

    public record DecisionRequest(@NotNull Long version, @Size(max = 1000) String comment) {
    }

    public record ReverseExpenseRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record ExpenseResponse(UUID id, String number, ProjectRef project, CategoryRef category, String description,
                                  String vendorName, BigDecimal amount, LocalDate expenseDate, ExpenseStatus status,
                                  UserSummary createdBy, UserSummary submittedBy, Instant submittedAt,
                                  UserSummary decidedBy, Instant decidedAt, boolean reversal,
                                  UUID reversesExpenseId, boolean canDecide, Long version, Instant createdAt) {
    }

    public record ApprovalHistoryItem(ExpenseApproval.Action action, UserSummary actor, String comment, Instant at) {
    }

    public record CategorySummary(UUID categoryId, String categoryName, BigDecimal allocated, BigDecimal approved,
                                  BigDecimal pending, BigDecimal variance) {
    }

    /**
     * Metric definitions (blueprint 11.4): approved spend counts APPROVED expenses (reversals are negative,
     * so they net out), pending spend counts SUBMITTED ones, drafts and rejected expenses count nowhere.
     */
    public record FinancialSummaryResponse(UUID projectId, String currencyCode, BigDecimal approvedBudget,
                                           BigDecimal approvedSpend, BigDecimal pendingSpend, BigDecimal remainingBudget,
                                           BigDecimal utilizationPercent, BigDecimal projectedUtilizationPercent,
                                           List<CategorySummary> categories) {
    }
}
