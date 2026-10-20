package com.constructflow.finance.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.finance.dto.FinanceDtos.ApprovalHistoryItem;
import com.constructflow.finance.dto.FinanceDtos.BudgetAllocationsResponse;
import com.constructflow.finance.dto.FinanceDtos.BudgetCategoryResponse;
import com.constructflow.finance.dto.FinanceDtos.CreateBudgetCategoryRequest;
import com.constructflow.finance.dto.FinanceDtos.CreateExpenseRequest;
import com.constructflow.finance.dto.FinanceDtos.DecisionRequest;
import com.constructflow.finance.dto.FinanceDtos.ExpenseResponse;
import com.constructflow.finance.dto.FinanceDtos.FinancialSummaryResponse;
import com.constructflow.finance.dto.FinanceDtos.ReplaceAllocationsRequest;
import com.constructflow.finance.dto.FinanceDtos.ReverseExpenseRequest;
import com.constructflow.finance.entity.ExpenseStatus;
import com.constructflow.finance.service.BudgetCategoryService;
import com.constructflow.finance.service.BudgetService;
import com.constructflow.finance.service.ExpenseService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Finance")
public class FinanceController {

    private final BudgetCategoryService categoryService;
    private final BudgetService budgetService;
    private final ExpenseService expenseService;

    public FinanceController(BudgetCategoryService categoryService, BudgetService budgetService,
                             ExpenseService expenseService) {
        this.categoryService = categoryService;
        this.budgetService = budgetService;
        this.expenseService = expenseService;
    }

    @GetMapping("/budget-categories")
    public List<BudgetCategoryResponse> categories(@AuthenticationPrincipal CurrentUser user) {
        return categoryService.list(user);
    }

    @PostMapping("/budget-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public BudgetCategoryResponse createCategory(@AuthenticationPrincipal CurrentUser user,
                                                 @Valid @RequestBody CreateBudgetCategoryRequest request) {
        return categoryService.create(user, request.name());
    }

    @GetMapping("/projects/{projectId}/budget-allocations")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public BudgetAllocationsResponse allocations(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        return budgetService.get(user, projectId);
    }

    @PutMapping("/projects/{projectId}/budget-allocations")
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    @Operation(summary = "Replace the project's category allocations (sum must not exceed the approved budget)")
    public BudgetAllocationsResponse replaceAllocations(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable UUID projectId,
                                                        @Valid @RequestBody ReplaceAllocationsRequest request) {
        return budgetService.replace(user, projectId, request.allocations());
    }

    @GetMapping("/projects/{projectId}/financial-summary")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public FinancialSummaryResponse financialSummary(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        return expenseService.financialSummary(user, projectId);
    }

    @GetMapping("/projects/{projectId}/expenses")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public PageResponse<ExpenseResponse> projectExpenses(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable UUID projectId,
                                                         @RequestParam(required = false) ExpenseStatus status,
                                                         @RequestParam(required = false) UUID categoryId,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return expenseService.search(user, projectId, status, categoryId, from, to, false, pageable);
    }

    @PostMapping("/projects/{projectId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ExpenseResponse createExpense(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                         @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.create(user, projectId, request);
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @Operation(summary = "Expenses visible to the caller across projects (e.g. ?status=SUBMITTED for the approval queue)")
    public PageResponse<ExpenseResponse> expenses(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestParam(required = false) UUID projectId,
                                                  @RequestParam(required = false) ExpenseStatus status,
                                                  @RequestParam(required = false) UUID categoryId,
                                                  @RequestParam(defaultValue = "false") boolean mine,
                                                  @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return expenseService.search(user, projectId, status, categoryId, null, null, mine, pageable);
    }

    @GetMapping("/expenses/{id}")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public ExpenseResponse expense(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return expenseService.get(user, id);
    }

    @PatchMapping("/expenses/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ExpenseResponse updateDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                       @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.updateDraft(user, id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ResponseEntity<Void> deleteDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        expenseService.deleteDraft(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/expenses/{id}/submit")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ExpenseResponse submit(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return expenseService.submit(user, id);
    }

    @PostMapping("/expenses/{id}/approve")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ExpenseResponse approve(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody DecisionRequest request) {
        return expenseService.approve(user, id, request);
    }

    @PostMapping("/expenses/{id}/reject")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ExpenseResponse reject(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                  @Valid @RequestBody DecisionRequest request) {
        return expenseService.reject(user, id, request);
    }

    @PostMapping("/expenses/{id}/reversal")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ExpenseResponse reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody ReverseExpenseRequest request) {
        return expenseService.reverse(user, id, request.reason());
    }

    @GetMapping("/expenses/{id}/history")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public List<ApprovalHistoryItem> history(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return expenseService.history(user, id);
    }
}
