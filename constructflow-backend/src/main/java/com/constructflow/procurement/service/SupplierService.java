package com.constructflow.procurement.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.procurement.dto.ProcurementDtos.SupplierRequest;
import com.constructflow.procurement.dto.ProcurementDtos.SupplierResponse;
import com.constructflow.procurement.entity.Supplier;
import com.constructflow.procurement.repository.SupplierRepository;
import com.constructflow.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final AuditService auditService;

    public SupplierService(SupplierRepository suppliers, AuditService auditService) {
        this.suppliers = suppliers;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(CurrentUser user, String q, boolean includeInactive, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(suppliers.search(user.organizationId(), pattern, includeInactive, pageable),
                SupplierService::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(CurrentUser user, UUID id) {
        return toResponse(load(user.organizationId(), id));
    }

    @Transactional
    public SupplierResponse create(CurrentUser user, SupplierRequest request) {
        if (suppliers.existsByOrganizationIdAndNameIgnoreCase(user.organizationId(), request.name().trim())) {
            throw new ConflictException("DUPLICATE_SUPPLIER", "A supplier with this name already exists.");
        }
        Supplier supplier = new Supplier(user.organizationId(), request.name().trim());
        apply(supplier, request);
        supplier = suppliers.save(supplier);
        auditService.record(user, AuditAction.SUPPLIER_CREATED, "Supplier", supplier.getId(), null,
                "Created supplier " + supplier.getName(), Map.of());
        suppliers.flush();
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse update(CurrentUser user, UUID id, SupplierRequest request) {
        Supplier supplier = load(user.organizationId(), id);
        OptimisticLock.check(request.version(), supplier.getVersion(), "Supplier");
        apply(supplier, request);
        suppliers.flush();
        auditService.record(user, AuditAction.SUPPLIER_UPDATED, "Supplier", supplier.getId(), null,
                "Updated supplier " + supplier.getName(), Map.of("active", supplier.isActive()));
        return toResponse(supplier);
    }

    @Transactional(readOnly = true)
    public Supplier load(UUID organizationId, UUID id) {
        return suppliers.findByIdAndOrganizationId(id, organizationId).orElseThrow(() -> new NotFoundException("Supplier"));
    }

    private static void apply(Supplier supplier, SupplierRequest r) {
        supplier.update(r.name().trim(), blank(r.contactName()), blank(r.email()), blank(r.phone()), blank(r.address()),
                blank(r.taxId()), r.active() == null || r.active());
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactName(), s.getEmail(), s.getPhone(),
                s.getAddress(), s.getTaxId(), s.isActive(), s.getVersion());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
