package com.constructflow.finance.entity;

/**
 * DRAFT -> SUBMITTED -> APPROVED | REJECTED. Approved and rejected are final: an approved expense is
 * corrected with a reversal, a rejected one by submitting a new expense.
 */
public enum ExpenseStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED
}
