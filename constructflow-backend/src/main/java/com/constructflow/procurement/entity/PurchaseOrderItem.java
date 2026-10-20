package com.constructflow.procurement.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.inventory.entity.Material;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityOrdered;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityReceived;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(Material material, BigDecimal quantityOrdered, BigDecimal unitPrice) {
        this.material = material;
        this.quantityOrdered = quantityOrdered;
        this.unitPrice = unitPrice;
        this.quantityReceived = BigDecimal.ZERO;
        // Line totals are rounded once, to cents, using banker's rounding (ADR-0005).
        this.lineTotal = quantityOrdered.multiply(unitPrice).setScale(2, RoundingMode.HALF_EVEN);
    }

    void attachTo(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public BigDecimal remaining() {
        return quantityOrdered.subtract(quantityReceived);
    }

    public void receive(BigDecimal quantity) {
        if (quantity.compareTo(remaining()) > 0) {
            throw new com.constructflow.common.exception.ConflictException("OVER_RECEIPT",
                    "Cannot receive %s of %s: only %s outstanding.".formatted(quantity.stripTrailingZeros().toPlainString(),
                            material.getCode(), remaining().stripTrailingZeros().toPlainString()));
        }
        quantityReceived = quantityReceived.add(quantity);
    }

    public boolean isFullyReceived() {
        return quantityReceived.compareTo(quantityOrdered) >= 0;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public Material getMaterial() {
        return material;
    }

    public BigDecimal getQuantityOrdered() {
        return quantityOrdered;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
