package com.constructflow.finance.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import com.constructflow.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** The share of a project's approved budget planned for one category. */
@Entity
@Table(name = "budget_allocations")
public class BudgetAllocation extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", updatable = false)
    private BudgetCategory category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected BudgetAllocation() {
    }

    public BudgetAllocation(Project project, BudgetCategory category, BigDecimal amount) {
        this.project = project;
        this.category = category;
        this.amount = amount;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Project getProject() {
        return project;
    }

    public BudgetCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
