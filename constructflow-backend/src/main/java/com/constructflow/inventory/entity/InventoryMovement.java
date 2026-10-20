package com.constructflow.inventory.entity;

import com.constructflow.common.persistence.BaseEntity;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable line of the stock ledger. {@code balanceAfter} makes each line self-explanatory in audits. */
@Entity
@Immutable
@Table(name = "inventory_movements")
public class InventoryMovement extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal balanceAfter;

    private String reason;

    private String referenceType;

    private UUID referenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(Material material, Project project, MovementType type, BigDecimal quantity,
                             BigDecimal balanceAfter, String reason, String referenceType, UUID referenceId, User actor) {
        this.organizationId = material.getOrganizationId();
        this.material = material;
        this.project = project;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.actor = actor;
    }

    public Material getMaterial() {
        return material;
    }

    public Project getProject() {
        return project;
    }

    public MovementType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public User getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
