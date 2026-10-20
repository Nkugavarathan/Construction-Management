package com.constructflow.procurement.entity;

import com.constructflow.common.exception.InvalidTransitionException;
import com.constructflow.common.persistence.TimestampedEntity;
import com.constructflow.project.entity.Project;
import com.constructflow.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends TimestampedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    private LocalDate expectedDate;

    private String notes;

    private String statusReason;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private Instant approvedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @Version
    private Long version;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(UUID organizationId, String number, Supplier supplier, Project project, LocalDate expectedDate,
                         String notes, User createdBy) {
        this.organizationId = organizationId;
        this.number = number;
        this.supplier = supplier;
        this.project = project;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.createdBy = createdBy;
        this.status = PurchaseOrderStatus.DRAFT;
        this.totalAmount = BigDecimal.ZERO;
    }

    public void replaceDraft(Supplier supplier, Project project, LocalDate expectedDate, String notes,
                             List<PurchaseOrderItem> newItems) {
        this.supplier = supplier;
        this.project = project;
        this.expectedDate = expectedDate;
        this.notes = notes;
        items.clear();
        newItems.forEach(this::addItem);
    }

    public void addItem(PurchaseOrderItem item) {
        item.attachTo(this);
        items.add(item);
        totalAmount = items.stream().map(PurchaseOrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void transitionTo(PurchaseOrderStatus target, String reason) {
        if (!status.allowedTargets().contains(target)) {
            throw new InvalidTransitionException("Purchase order", status, target, status.allowedTargets());
        }
        this.status = target;
        if (reason != null) {
            this.statusReason = reason;
        }
    }

    public void markSubmitted(User submitter) {
        transitionTo(PurchaseOrderStatus.SUBMITTED, null);
        this.submittedBy = submitter;
    }

    public void markApproved(User approver, Instant now) {
        transitionTo(PurchaseOrderStatus.APPROVED, null);
        this.approvedBy = approver;
        this.approvedAt = now;
    }

    /** Called after a goods receipt has updated line quantities. */
    public void refreshReceivingStatus() {
        boolean all = items.stream().allMatch(PurchaseOrderItem::isFullyReceived);
        transitionTo(all ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED, null);
    }

    public boolean hasReceivedAnything() {
        return items.stream().anyMatch(i -> i.getQuantityReceived().signum() > 0);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getNumber() {
        return number;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Project getProject() {
        return project;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public String getNotes() {
        return notes;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public List<PurchaseOrderItem> getItems() {
        return items;
    }

    public Long getVersion() {
        return version;
    }
}
