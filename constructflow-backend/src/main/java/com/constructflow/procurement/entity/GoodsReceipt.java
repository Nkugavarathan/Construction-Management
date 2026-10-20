package com.constructflow.procurement.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One delivery booked against a purchase order. Identified for retries by its idempotency key. */
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private String deliveryNoteRef;

    @Column(nullable = false)
    private String idempotencyKey;

    /** SHA-256 of the canonical request, to detect a key reused with a different payload. */
    @Column(nullable = false)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    private String notes;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL)
    private List<GoodsReceiptItem> items = new ArrayList<>();

    protected GoodsReceipt() {
    }

    public GoodsReceipt(PurchaseOrder purchaseOrder, String deliveryNoteRef, String idempotencyKey, String requestHash,
                        User receivedBy, String notes) {
        this.organizationId = purchaseOrder.getOrganizationId();
        this.purchaseOrder = purchaseOrder;
        this.deliveryNoteRef = deliveryNoteRef;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.receivedBy = receivedBy;
        this.notes = notes;
    }

    public void addItem(GoodsReceiptItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public String getDeliveryNoteRef() {
        return deliveryNoteRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<GoodsReceiptItem> getItems() {
        return items;
    }
}
