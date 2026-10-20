package com.constructflow.inventory.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.constructflow.inventory.dto.InventoryDtos.CreateMaterialRequest;
import com.constructflow.inventory.dto.InventoryDtos.IssueRequest;
import com.constructflow.inventory.dto.InventoryDtos.MaterialResponse;
import com.constructflow.inventory.dto.InventoryDtos.MovementResponse;
import com.constructflow.inventory.dto.InventoryDtos.ReceiptRequest;
import com.constructflow.inventory.dto.InventoryDtos.UpdateMaterialRequest;
import com.constructflow.inventory.service.InventoryService;
import com.constructflow.inventory.service.MaterialService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inventory")
public class InventoryController {

    private final MaterialService materialService;
    private final InventoryService inventoryService;

    public InventoryController(MaterialService materialService, InventoryService inventoryService) {
        this.materialService = materialService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/materials")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public PageResponse<MaterialResponse> materials(@AuthenticationPrincipal CurrentUser user,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "false") boolean lowStock,
                                                    @RequestParam(defaultValue = "false") boolean includeInactive,
                                                    @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return materialService.search(user, q, lowStock, includeInactive, pageable);
    }

    @PostMapping("/materials")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MATERIAL_MANAGE')")
    public MaterialResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateMaterialRequest request) {
        return materialService.create(user, request);
    }

    @GetMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public MaterialResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return materialService.get(user, id);
    }

    @PatchMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('MATERIAL_MANAGE')")
    public MaterialResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateMaterialRequest request) {
        return materialService.update(user, id, request);
    }

    @GetMapping("/materials/{id}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public PageResponse<MovementResponse> movements(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                                    @PageableDefault(size = 25, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.history(user, id, pageable);
    }

    @PostMapping("/inventory/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
    public MovementResponse receive(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ReceiptRequest request) {
        return inventoryService.receive(user, request);
    }

    @PostMapping("/inventory/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_ISSUE')")
    public MovementResponse issue(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody IssueRequest request) {
        return inventoryService.issue(user, request);
    }

    @PostMapping("/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    public MovementResponse adjust(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AdjustmentRequest request) {
        return inventoryService.adjust(user, request);
    }
}
