package com.constructflow.inventory.dto;

import com.constructflow.inventory.entity.MaterialUnit;
import com.constructflow.inventory.entity.MovementType;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record CreateMaterialRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,32}", message = "must be 2-32 letters, digits or dashes") String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull MaterialUnit unit,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal reorderLevel) {
    }

    public record UpdateMaterialRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull MaterialUnit unit,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal reorderLevel,
            @NotNull Boolean active,
            @NotNull Long version) {
    }

    public record MaterialResponse(UUID id, String code, String name, MaterialUnit unit, BigDecimal reorderLevel,
                                   BigDecimal quantityOnHand, boolean lowStock, boolean active, Long version) {
    }

    public record ReceiptRequest(
            @NotNull UUID materialId,
            @NotNull @DecimalMin(value = "0.001", message = "must be greater than zero") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record IssueRequest(
            @NotNull UUID materialId,
            @NotNull UUID projectId,
            @NotNull @DecimalMin(value = "0.001", message = "must be greater than zero") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @Size(max = 500) String note) {
    }

    public record AdjustmentRequest(
            @NotNull UUID materialId,
            @NotNull @Digits(integer = 11, fraction = 3) BigDecimal quantityDelta,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record ProjectRef(UUID id, String code, String name) {
    }

    public record MovementResponse(UUID id, UUID materialId, MovementType type, BigDecimal quantity,
                                   BigDecimal balanceAfter, ProjectRef project, String reason, String referenceType,
                                   UUID referenceId, UserSummary actor, Instant occurredAt) {
    }

    public record ProjectMaterialUsage(UUID materialId, String code, String name, MaterialUnit unit,
                                       BigDecimal quantity) {
    }
}
