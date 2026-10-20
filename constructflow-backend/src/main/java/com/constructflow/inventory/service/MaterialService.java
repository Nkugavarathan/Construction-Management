package com.constructflow.inventory.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.inventory.dto.InventoryDtos.CreateMaterialRequest;
import com.constructflow.inventory.dto.InventoryDtos.MaterialResponse;
import com.constructflow.inventory.dto.InventoryDtos.UpdateMaterialRequest;
import com.constructflow.inventory.entity.Material;
import com.constructflow.inventory.repository.MaterialRepository;
import com.constructflow.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Material catalogue. Stock quantities are never edited here, only through {@link InventoryService}. */
@Service
public class MaterialService {

    private final MaterialRepository materials;
    private final AuditService auditService;

    public MaterialService(MaterialRepository materials, AuditService auditService) {
        this.materials = materials;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> search(CurrentUser user, String q, boolean lowStockOnly,
                                                 boolean includeInactive, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(materials.search(user.organizationId(), pattern, lowStockOnly, includeInactive, pageable),
                MaterialService::toResponse);
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(CurrentUser user, UUID id) {
        return toResponse(load(user, id));
    }

    @Transactional
    public MaterialResponse create(CurrentUser user, CreateMaterialRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (materials.existsByOrganizationIdAndCodeIgnoreCase(user.organizationId(), code)) {
            throw new ConflictException("DUPLICATE_MATERIAL_CODE", "Material code %s already exists.".formatted(code));
        }
        Material material = materials.save(new Material(user.organizationId(), code, request.name().trim(),
                request.unit(), request.reorderLevel()));
        auditService.record(user, AuditAction.MATERIAL_CREATED, "Material", material.getId(), null,
                "Created material %s %s".formatted(code, material.getName()), Map.of("unit", request.unit()));
        materials.flush();
        return toResponse(material);
    }

    @Transactional
    public MaterialResponse update(CurrentUser user, UUID id, UpdateMaterialRequest request) {
        Material material = load(user, id);
        OptimisticLock.check(request.version(), material.getVersion(), "Material");
        material.update(request.name().trim(), request.unit(), request.reorderLevel(), request.active());
        materials.flush();
        auditService.record(user, AuditAction.MATERIAL_UPDATED, "Material", material.getId(), null,
                "Updated material " + material.getCode(),
                Map.of("reorderLevel", request.reorderLevel(), "active", request.active()));
        return toResponse(material);
    }

    @Transactional(readOnly = true)
    public long countLowStock(UUID organizationId) {
        return materials.countLowStock(organizationId);
    }

    @Transactional(readOnly = true)
    public Material load(CurrentUser user, UUID id) {
        return materials.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Material"));
    }

    public static MaterialResponse toResponse(Material m) {
        return new MaterialResponse(m.getId(), m.getCode(), m.getName(), m.getUnit(), m.getReorderLevel(),
                m.getQuantityOnHand(), m.isLowStock(), m.isActive(), m.getVersion());
    }
}
