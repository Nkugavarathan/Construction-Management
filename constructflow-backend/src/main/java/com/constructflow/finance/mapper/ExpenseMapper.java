package com.constructflow.finance.mapper;

import com.constructflow.finance.dto.FinanceDtos.CategoryRef;
import com.constructflow.finance.dto.FinanceDtos.ExpenseResponse;
import com.constructflow.finance.dto.FinanceDtos.ProjectRef;
import com.constructflow.finance.entity.Expense;
import com.constructflow.user.dto.UserSummary;

public final class ExpenseMapper {

    private ExpenseMapper() {
    }

    public static ExpenseResponse toResponse(Expense e, boolean canDecide) {
        return new ExpenseResponse(e.getId(), e.getNumber(),
                new ProjectRef(e.getProject().getId(), e.getProject().getCode(), e.getProject().getName()),
                new CategoryRef(e.getCategory().getId(), e.getCategory().getName()),
                e.getDescription(), e.getVendorName(), e.getAmount(), e.getExpenseDate(), e.getStatus(),
                UserSummary.of(e.getCreatedBy()), UserSummary.of(e.getSubmittedBy()), e.getSubmittedAt(),
                UserSummary.of(e.getDecidedBy()), e.getDecidedAt(), e.isReversal(),
                e.getReversesExpense() != null ? e.getReversesExpense().getId() : null,
                canDecide, e.getVersion(), e.getCreatedAt());
    }
}
