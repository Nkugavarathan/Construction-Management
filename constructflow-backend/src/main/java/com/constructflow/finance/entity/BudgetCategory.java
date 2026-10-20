package com.constructflow.finance.entity;

import com.constructflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Organization-level cost category (Labour, Materials, ...), shared by all projects. */
@Entity
@Table(name = "budget_categories")
public class BudgetCategory extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BudgetCategory() {
    }

    public BudgetCategory(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name.trim();
        this.active = true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
