package com.constructflow.procurement.entity;

import com.constructflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id")
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id")
    private PurchaseOrderItem purchaseOrderItem;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    protected GoodsReceiptItem() {
    }

    public GoodsReceiptItem(PurchaseOrderItem purchaseOrderItem, BigDecimal quantity) {
        this.purchaseOrderItem = purchaseOrderItem;
        this.quantity = quantity;
    }

    void attachTo(GoodsReceipt goodsReceipt) {
        this.goodsReceipt = goodsReceipt;
    }

    public PurchaseOrderItem getPurchaseOrderItem() {
        return purchaseOrderItem;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
