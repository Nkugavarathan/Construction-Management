package com.constructflow.finance.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One step in an expense's approval history. Append-only. */
@Entity
@Immutable
@Table(name = "expense_approvals")
public class ExpenseApproval extends BaseEntity {

    public enum Action { SUBMITTED, APPROVED, REJECTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpenseApproval() {
    }

    public ExpenseApproval(Expense expense, Action action, User actor, String comment) {
        this.expense = expense;
        this.action = action;
        this.actor = actor;
        this.comment = comment;
    }

    public Expense getExpense() {
        return expense;
    }

    public Action getAction() {
        return action;
    }

    public User getActor() {
        return actor;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
