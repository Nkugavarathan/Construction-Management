package com.constructflow.procurement.controller;

import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.response.PageResponse;
import com.constructflow.procurement.dto.ProcurementDtos.GoodsReceiptResponse;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderRequest;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderResponse;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderSummary;
import com.constructflow.procurement.dto.ProcurementDtos.ReceiveRequest;
import com.constructflow.procurement.dto.ProcurementDtos.StatusChangeRequest;
import com.constructflow.procurement.dto.ProcurementDtos.SupplierRequest;
import com.constructflow.procurement.dto.ProcurementDtos.SupplierResponse;
import com.constructflow.procurement.entity.PurchaseOrderStatus;
import com.constructflow.procurement.service.PurchaseOrderService;
import com.constructflow.procurement.service.PurchaseOrderService.ReceiveResult;
import com.constructflow.procurement.service.SupplierService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Procurement")
public class ProcurementController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,80}");

    private final SupplierService supplierService;
    private final PurchaseOrderService orderService;

    public ProcurementController(SupplierService supplierService, PurchaseOrderService orderService) {
        this.supplierService = supplierService;
        this.orderService = orderService;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('PROCUREMENT_READ')")
    public PageResponse<SupplierResponse> suppliers(@AuthenticationPrincipal CurrentUser user,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "false") boolean includeInactive,
                                                    @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return supplierService.search(user, q, includeInactive, pageable);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierResponse createSupplier(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SupplierRequest request) {
        return supplierService.create(user, request);
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('PROCUREMENT_READ')")
    public SupplierResponse supplier(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return supplierService.get(user, id);
    }

    @PatchMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierResponse updateSupplier(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                           @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(user, id, request);
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('PROCUREMENT_READ')")
    public PageResponse<PurchaseOrderSummary> orders(@AuthenticationPrincipal CurrentUser user,
                                                     @RequestParam(required = false) PurchaseOrderStatus status,
                                                     @RequestParam(required = false) UUID supplierId,
                                                     @RequestParam(required = false) UUID projectId,
                                                     @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.search(user, status, supplierId, projectId, pageable);
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PO_CREATE')")
    public PurchaseOrderResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PurchaseOrderRequest request) {
        return orderService.create(user, request);
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAuthority('PROCUREMENT_READ')")
    public PurchaseOrderResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return orderService.get(user, id);
    }

    @PutMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAuthority('PO_CREATE')")
    public PurchaseOrderResponse updateDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                             @Valid @RequestBody PurchaseOrderRequest request) {
        return orderService.updateDraft(user, id, request);
    }

    @PostMapping("/purchase-orders/{id}/submit")
    @PreAuthorize("hasAuthority('PO_CREATE')")
    public PurchaseOrderResponse submit(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                        @Valid @RequestBody StatusChangeRequest request) {
        return orderService.submit(user, id, request);
    }

    @PostMapping("/purchase-orders/{id}/approve")
    @PreAuthorize("hasAuthority('PO_APPROVE')")
    public PurchaseOrderResponse approve(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                         @Valid @RequestBody StatusChangeRequest request) {
        return orderService.approve(user, id, request);
    }

    @PostMapping("/purchase-orders/{id}/return")
    @PreAuthorize("hasAuthority('PO_APPROVE')")
    public PurchaseOrderResponse returnToDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                               @Valid @RequestBody StatusChangeRequest request) {
        return orderService.returnToDraft(user, id, request);
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('PO_CREATE', 'PO_APPROVE')")
    public PurchaseOrderResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                        @Valid @RequestBody StatusChangeRequest request) {
        return orderService.cancel(user, id, request);
    }

    @PostMapping("/purchase-orders/{id}/close")
    @PreAuthorize("hasAuthority('PO_APPROVE')")
    public PurchaseOrderResponse close(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                       @Valid @RequestBody StatusChangeRequest request) {
        return orderService.close(user, id, request);
    }

    @PostMapping("/purchase-orders/{id}/receipts")
    @PreAuthorize("hasAuthority('PO_RECEIVE')")
    @Operation(summary = "Book a delivery. Requires an Idempotency-Key header; a retry with the same key returns the original receipt (200).")
    public ResponseEntity<GoodsReceiptResponse> receive(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                                        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                        @Valid @RequestBody ReceiveRequest request) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED",
                    "An Idempotency-Key header (8-80 letters, digits, '-' or '_') is required.");
        }
        ReceiveResult result = orderService.receive(user, id, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.receipt());
    }

    @GetMapping("/purchase-orders/{id}/receipts")
    @PreAuthorize("hasAuthority('PROCUREMENT_READ')")
    public List<GoodsReceiptResponse> receipts(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return orderService.receipts(user, id);
    }
}
