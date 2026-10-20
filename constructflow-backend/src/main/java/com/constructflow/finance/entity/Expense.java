package com.constructflow.finance.entity;

import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.persistence.TimestampedEntity;
import com.constructflow.project.entity.Project;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A project cost. State changes go through methods that check the current status, so an approved
 * expense cannot be edited by any code path. The {@code @Version} column makes two approvers acting
 * at the same moment collide: the second UPDATE matches no row and fails with a 409.
 */
@Entity
@Table(name = "expenses")
public class Expense extends TimestampedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private BudgetCategory category;

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(nullable = false)
    private String description;

    private String vendorName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_expense_id", updatable = false)
    private Expense reversesExpense;

    @Version
    private Long version;

    protected Expense() {
    }

    public Expense(Project project, BudgetCategory category, String number, String description, String vendorName,
                   BigDecimal amount, LocalDate expenseDate, User createdBy) {
        this.organizationId = project.getOrganizationId();
        this.project = project;
        this.category = category;
        this.number = number;
        this.description = description;
        this.vendorName = vendorName;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.createdBy = createdBy;
        this.status = ExpenseStatus.DRAFT;
    }

    /** A reversal cancels an approved expense with an equal negative amount, and is itself approved normally. */
    public static Expense reversalOf(Expense original, String number, String reason, LocalDate date, User requester,
                                     Instant now) {
        Expense reversal = new Expense(original.project, original.category, number,
                "Reversal of %s: %s".formatted(original.number, reason), original.vendorName,
                original.amount.negate(), date, requester);
        reversal.reversesExpense = original;
        reversal.status = ExpenseStatus.SUBMITTED;
        reversal.submittedBy = requester;
        reversal.submittedAt = now;
        return reversal;
    }

    public void editDraft(BudgetCategory category, String description, String vendorName, BigDecimal amount,
                          LocalDate expenseDate) {
        requireStatus(ExpenseStatus.DRAFT, "edited");
        this.category = category;
        this.description = description;
        this.vendorName = vendorName;
        this.amount = amount;
        this.expenseDate = expenseDate;
    }

    public void submit(User submitter, Instant now) {
        requireStatus(ExpenseStatus.DRAFT, "submitted");
        this.status = ExpenseStatus.SUBMITTED;
        this.submittedBy = submitter;
        this.submittedAt = now;
    }

    public void approve(User approver, Instant now) {
        decide(ExpenseStatus.APPROVED, approver, now);
    }

    public void reject(User approver, Instant now) {
        decide(ExpenseStatus.REJECTED, approver, now);
    }

    private void decide(ExpenseStatus outcome, User approver, Instant now) {
        requireStatus(ExpenseStatus.SUBMITTED, outcome == ExpenseStatus.APPROVED ? "approved" : "rejected");
        this.status = outcome;
        this.decidedBy = approver;
        this.decidedAt = now;
    }

    public void requireStatus(ExpenseStatus expected, String action) {
        if (status != expected) {
            throw new ConflictException("INVALID_EXPENSE_STATE",
                    "Expense %s is %s and cannot be %s.".formatted(number, status, action));
        }
    }

    public boolean isReversal() {
        return reversesExpense != null;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Project getProject() {
        return project;
    }

    public BudgetCategory getCategory() {
        return category;
    }

    public String getNumber() {
        return number;
    }

    public String getDescription() {
        return description;
    }

    public String getVendorName() {
        return vendorName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Expense getReversesExpense() {
        return reversesExpense;
    }

    public Long getVersion() {
        return version;
    }
}
