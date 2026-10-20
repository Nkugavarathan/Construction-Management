package com.constructflow.inventory.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.response.PageResponse;
import com.constructflow.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.constructflow.inventory.dto.InventoryDtos.IssueRequest;
import com.constructflow.inventory.dto.InventoryDtos.MovementResponse;
import com.constructflow.inventory.dto.InventoryDtos.ProjectMaterialUsage;
import com.constructflow.inventory.dto.InventoryDtos.ProjectRef;
import com.constructflow.inventory.dto.InventoryDtos.ReceiptRequest;
import com.constructflow.inventory.entity.InventoryMovement;
import com.constructflow.inventory.entity.Material;
import com.constructflow.inventory.entity.MovementType;
import com.constructflow.inventory.repository.InventoryMovementRepository;
import com.constructflow.inventory.repository.MaterialRepository;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.organization.service.MemberService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.user.dto.UserSummary;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All stock changes go through {@link #post}: lock the material row, check and update the cached
 * balance, append the ledger line, write the audit event, all in one transaction. Two storekeepers
 * issuing the last 10 bags at the same moment are serialized by the row lock; the second sees the
 * reduced balance and gets a 409 instead of driving stock negative (ADR-0004).
 */
@Service
public class InventoryService {

    private final MaterialRepository materials;
    private final InventoryMovementRepository movements;
    private final ProjectAccessPolicy projectAccess;
    private final NotificationService notifications;
    private final MemberService members;
    private final AuditService auditService;
    private final UserRepository users;

    public InventoryService(MaterialRepository materials, InventoryMovementRepository movements,
                            ProjectAccessPolicy projectAccess, NotificationService notifications, MemberService members,
                            AuditService auditService, UserRepository users) {
        this.materials = materials;
        this.movements = movements;
        this.projectAccess = projectAccess;
        this.notifications = notifications;
        this.members = members;
        this.auditService = auditService;
        this.users = users;
    }

    @Transactional
    public MovementResponse receive(CurrentUser user, ReceiptRequest request) {
        InventoryMovement movement = post(user, request.materialId(), null, MovementType.RECEIPT, request.quantity(),
                request.reason().trim(), "MANUAL", null);
        return toResponse(movement);
    }

    @Transactional
    public MovementResponse issue(CurrentUser user, IssueRequest request) {
        Project project = projectAccess.requireReadable(user, request.projectId());
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new ConflictException("PROJECT_NOT_ACTIVE", "Materials can only be issued to active projects.");
        }
        InventoryMovement movement = post(user, request.materialId(), project, MovementType.ISSUE,
                request.quantity().negate(), blankToNull(request.note()), null, null);
        return toResponse(movement);
    }

    @Transactional
    public MovementResponse adjust(CurrentUser user, AdjustmentRequest request) {
        if (request.quantityDelta().signum() == 0) {
            throw new BadRequestException("ZERO_ADJUSTMENT", "quantityDelta", "Adjustment cannot be zero.");
        }
        InventoryMovement movement = post(user, request.materialId(), null, MovementType.ADJUSTMENT,
                request.quantityDelta(), request.reason().trim(), null, null);
        return toResponse(movement);
    }

    /** Used by procurement: posts a goods-receipt line inside the receiving transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void receiveFromPurchaseOrder(CurrentUser user, UUID materialId, BigDecimal quantity, UUID goodsReceiptId,
                                         String purchaseOrderNumber) {
        post(user, materialId, null, MovementType.RECEIPT, quantity, "Received against " + purchaseOrderNumber,
                "GOODS_RECEIPT", goodsReceiptId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> history(CurrentUser user, UUID materialId, Pageable pageable) {
        materials.findByIdAndOrganizationId(materialId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Material"));
        return PageResponse.of(movements.findByMaterialId(materialId, pageable), InventoryService::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ProjectMaterialUsage> usageForProject(UUID projectId) {
        return movements.usageByProject(projectId).stream()
                .map(u -> new ProjectMaterialUsage(u.getMaterialId(), u.getCode(), u.getName(), u.getUnit(), u.getQuantity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal ledgerBalance(UUID materialId) {
        return movements.ledgerBalance(materialId);
    }

    private InventoryMovement post(CurrentUser user, UUID materialId, Project project, MovementType type,
                                   BigDecimal signedQuantity, String reason, String referenceType, UUID referenceId) {
        Material material = materials.findForUpdate(materialId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Material"));
        if (!material.isActive()) {
            throw new ConflictException("MATERIAL_INACTIVE", "Material %s is inactive.".formatted(material.getCode()));
        }
        boolean wasLow = material.isLowStock();
        BigDecimal balance = material.applyMovement(signedQuantity);
        InventoryMovement movement = movements.save(new InventoryMovement(material, project, type, signedQuantity, balance,
                reason, referenceType, referenceId, users.getReferenceById(user.userId())));

        AuditAction action = switch (type) {
            case RECEIPT, RETURN -> AuditAction.INVENTORY_RECEIVED;
            case ISSUE -> AuditAction.INVENTORY_ISSUED;
            case ADJUSTMENT -> AuditAction.INVENTORY_ADJUSTED;
        };
        auditService.record(user, action, "Material", material.getId(), project != null ? project.getId() : null,
                "%s %s %s %s (balance %s)".formatted(type, signedQuantity.stripTrailingZeros().toPlainString(),
                        material.getUnit(), material.getCode(), balance.stripTrailingZeros().toPlainString()),
                reason == null ? Map.of("quantity", signedQuantity) : Map.of("quantity", signedQuantity, "reason", reason));

        if (!wasLow && material.isLowStock()) {
            notifications.notifyAll(user.organizationId(),
                    members.activeUserIdsWithRoles(user.organizationId(), List.of(Role.STOREKEEPER, Role.ADMIN)),
                    null, NotificationType.LOW_STOCK, "Low stock: " + material.getName(),
                    "%s at %s %s (reorder level %s)".formatted(material.getCode(),
                            balance.stripTrailingZeros().toPlainString(), material.getUnit(),
                            material.getReorderLevel().stripTrailingZeros().toPlainString()),
                    "/inventory/" + material.getId());
        }
        return movement;
    }

    private static MovementResponse toResponse(InventoryMovement m) {
        Project p = m.getProject();
        return new MovementResponse(m.getId(), m.getMaterial().getId(), m.getType(), m.getQuantity(), m.getBalanceAfter(),
                p != null ? new ProjectRef(p.getId(), p.getCode(), p.getName()) : null, m.getReason(),
                m.getReferenceType(), m.getReferenceId(), UserSummary.of(m.getActor()), m.getOccurredAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
