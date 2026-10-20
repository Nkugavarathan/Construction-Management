package com.constructflow.procurement.dto;

import com.constructflow.inventory.entity.MaterialUnit;
import com.constructflow.procurement.entity.PurchaseOrderStatus;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProcurementDtos {

    private ProcurementDtos() {
    }

    public record SupplierRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactName,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 300) String address,
            @Size(max = 40) String taxId,
            Boolean active,
            Long version) {
    }

    public record SupplierResponse(UUID id, String name, String contactName, String email, String phone,
                                   String address, String taxId, boolean active, Long version) {
    }

    public record PurchaseOrderLine(
            @NotNull UUID materialId,
            @NotNull @DecimalMin(value = "0.001", message = "must be greater than zero") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 4) BigDecimal unitPrice) {
    }

    public record PurchaseOrderRequest(
            @NotNull UUID supplierId,
            UUID projectId,
            LocalDate expectedDate,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<PurchaseOrderLine> items,
            Long version) {
    }

    public record StatusChangeRequest(@NotNull Long version, @Size(max = 500) String reason) {
    }

    public record ReceiveLine(@NotNull UUID purchaseOrderItemId,
                              @NotNull @DecimalMin(value = "0.001", message = "must be greater than zero") @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
    }

    public record ReceiveRequest(@NotBlank @Size(max = 80) String deliveryNoteRef, @Size(max = 1000) String notes,
                                 @NotEmpty @Valid List<ReceiveLine> items) {
    }

    public record Ref(UUID id, String code, String name) {
    }

    public record MaterialRef(UUID id, String code, String name, MaterialUnit unit) {
    }

    public record PurchaseOrderItemResponse(UUID id, MaterialRef material, BigDecimal quantityOrdered,
                                            BigDecimal quantityReceived, BigDecimal remaining, BigDecimal unitPrice,
                                            BigDecimal lineTotal) {
    }

    public record PurchaseOrderActions(boolean edit, boolean submit, boolean approve, boolean returnToDraft,
                                       boolean receive, boolean cancel, boolean close) {
    }

    public record PurchaseOrderSummary(UUID id, String number, Ref supplier, Ref project, PurchaseOrderStatus status,
                                       LocalDate expectedDate, BigDecimal totalAmount, UserSummary createdBy,
                                       Instant createdAt) {
    }

    public record PurchaseOrderResponse(UUID id, String number, Ref supplier, Ref project, PurchaseOrderStatus status,
                                        String statusReason, LocalDate expectedDate, String notes,
                                        BigDecimal totalAmount, UserSummary createdBy, UserSummary approvedBy,
                                        Instant approvedAt, List<PurchaseOrderItemResponse> items,
                                        PurchaseOrderActions actions, Long version, Instant createdAt) {
    }

    public record GoodsReceiptLineResponse(UUID purchaseOrderItemId, String materialCode, String materialName,
                                           BigDecimal quantity) {
    }

    public record GoodsReceiptResponse(UUID id, UUID purchaseOrderId, String deliveryNoteRef, UserSummary receivedBy,
                                       Instant receivedAt, String notes, List<GoodsReceiptLineResponse> items) {
    }
}
