package com.constructflow.inventory.entity;

import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Catalogue item plus its cached stock balance. The balance is changed only by
 * {@link #applyMovement(BigDecimal)}, which the inventory service calls while holding a row lock and
 * in the same transaction that writes the ledger entry.
 */
@Entity
@Table(name = "materials")
public class Material extends TimestampedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialUnit unit;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderLevel;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityOnHand;

    @Column(nullable = false)
    private boolean active;

    @Version
    private Long version;

    protected Material() {
    }

    public Material(UUID organizationId, String code, String name, MaterialUnit unit, BigDecimal reorderLevel) {
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.reorderLevel = reorderLevel;
        this.quantityOnHand = BigDecimal.ZERO;
        this.active = true;
    }

    public void update(String name, MaterialUnit unit, BigDecimal reorderLevel, boolean active) {
        if (unit != this.unit && quantityOnHand.signum() != 0) {
            throw new ConflictException("UNIT_CHANGE_WITH_STOCK",
                    "The unit can only be changed while stock on hand is zero.");
        }
        this.name = name;
        this.unit = unit;
        this.reorderLevel = reorderLevel;
        this.active = active;
    }

    /** Returns the new balance. Never lets stock go negative (also enforced by a CHECK constraint). */
    public BigDecimal applyMovement(BigDecimal signedQuantity) {
        BigDecimal next = quantityOnHand.add(signedQuantity);
        if (next.signum() < 0) {
            throw new InsufficientStockException(code, quantityOnHand, signedQuantity.negate());
        }
        quantityOnHand = next;
        return next;
    }

    public boolean isLowStock() {
        return quantityOnHand.compareTo(reorderLevel) <= 0;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public MaterialUnit getUnit() {
        return unit;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public BigDecimal getQuantityOnHand() {
        return quantityOnHand;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }
}
