package com.constructflow.procurement.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.persistence.OrgSequenceService;
import com.constructflow.common.response.PageResponse;
import com.constructflow.inventory.entity.Material;
import com.constructflow.inventory.service.InventoryService;
import com.constructflow.inventory.service.MaterialService;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.organization.service.MemberService;
import com.constructflow.procurement.dto.ProcurementDtos.GoodsReceiptLineResponse;
import com.constructflow.procurement.dto.ProcurementDtos.GoodsReceiptResponse;
import com.constructflow.procurement.dto.ProcurementDtos.MaterialRef;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderActions;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderItemResponse;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderLine;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderRequest;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderResponse;
import com.constructflow.procurement.dto.ProcurementDtos.PurchaseOrderSummary;
import com.constructflow.procurement.dto.ProcurementDtos.ReceiveLine;
import com.constructflow.procurement.dto.ProcurementDtos.ReceiveRequest;
import com.constructflow.procurement.dto.ProcurementDtos.Ref;
import com.constructflow.procurement.dto.ProcurementDtos.StatusChangeRequest;
import com.constructflow.procurement.entity.GoodsReceipt;
import com.constructflow.procurement.entity.GoodsReceiptItem;
import com.constructflow.procurement.entity.PurchaseOrder;
import com.constructflow.procurement.entity.PurchaseOrderItem;
import com.constructflow.procurement.entity.PurchaseOrderStatus;
import com.constructflow.procurement.entity.Supplier;
import com.constructflow.procurement.repository.GoodsReceiptRepository;
import com.constructflow.procurement.repository.PurchaseOrderRepository;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Permission;
import com.constructflow.security.Role;
import com.constructflow.user.dto.UserSummary;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase orders and goods receiving. Creation, approval and receiving are separate permissions held
 * by different roles, and nobody approves a purchase order they created (segregation of duties).
 */
@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final SupplierService suppliers;
    private final MaterialService materials;
    private final InventoryService inventory;
    private final ProjectAccessPolicy projectAccess;
    private final ProjectMemberService projectMembers;
    private final MemberService members;
    private final OrgSequenceService sequences;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final UserRepository users;
    private final Clock clock;

    public PurchaseOrderService(PurchaseOrderRepository orders, GoodsReceiptRepository receipts,
                                SupplierService suppliers, MaterialService materials, InventoryService inventory,
                                ProjectAccessPolicy projectAccess, ProjectMemberService projectMembers,
                                MemberService members, OrgSequenceService sequences, NotificationService notifications,
                                AuditService auditService, UserRepository users, Clock clock) {
        this.orders = orders;
        this.receipts = receipts;
        this.suppliers = suppliers;
        this.materials = materials;
        this.inventory = inventory;
        this.projectAccess = projectAccess;
        this.projectMembers = projectMembers;
        this.members = members;
        this.sequences = sequences;
        this.notifications = notifications;
        this.auditService = auditService;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderSummary> search(CurrentUser user, PurchaseOrderStatus status, UUID supplierId,
                                                     UUID projectId, Pageable pageable) {
        return PageResponse.of(orders.search(user.organizationId(), status, supplierId, projectId, pageable),
                PurchaseOrderService::toSummary);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(CurrentUser user, UUID id) {
        return toResponse(user, load(user, id));
    }

    @Transactional
    public PurchaseOrderResponse create(CurrentUser user, PurchaseOrderRequest request) {
        Supplier supplier = activeSupplier(user, request.supplierId());
        Project project = resolveProject(user, request.projectId());
        PurchaseOrder po = new PurchaseOrder(user.organizationId(), sequences.next(user.organizationId(), "PURCHASE_ORDER", "PO"),
                supplier, project, request.expectedDate(), blank(request.notes()), users.getReferenceById(user.userId()));
        buildItems(user, request.items()).forEach(po::addItem);
        po = orders.save(po);
        auditService.record(user, AuditAction.PURCHASE_ORDER_CREATED, "PurchaseOrder", po.getId(),
                project != null ? project.getId() : null,
                "Created %s for %s (%s)".formatted(po.getNumber(), supplier.getName(), po.getTotalAmount()),
                Map.of("totalAmount", po.getTotalAmount(), "lines", po.getItems().size()));
        orders.flush();
        return toResponse(user, po);
    }

    @Transactional
    public PurchaseOrderResponse updateDraft(CurrentUser user, UUID id, PurchaseOrderRequest request) {
        PurchaseOrder po = load(user, id);
        requireCreator(user, po);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("NOT_DRAFT", "Only draft purchase orders can be edited.");
        }
        OptimisticLock.check(request.version(), po.getVersion(), "Purchase order");
        po.replaceDraft(activeSupplier(user, request.supplierId()), resolveProject(user, request.projectId()),
                request.expectedDate(), blank(request.notes()), buildItems(user, request.items()));
        orders.flush();
        return toResponse(user, po);
    }

    @Transactional
    public PurchaseOrderResponse submit(CurrentUser user, UUID id, StatusChangeRequest request) {
        PurchaseOrder po = load(user, id);
        requireCreator(user, po);
        OptimisticLock.check(request.version(), po.getVersion(), "Purchase order");
        po.markSubmitted(users.getReferenceById(user.userId()));
        recordStatus(user, po, PurchaseOrderStatus.DRAFT, null);
        List<UUID> approvers = new ArrayList<>(members.activeUserIdsWithRoles(user.organizationId(),
                List.of(Role.FINANCE_OFFICER, Role.ADMIN)));
        if (po.getProject() != null) {
            approvers.addAll(projectMembers.userIdsWithRole(po.getProject().getId(), ProjectRole.MANAGER));
        }
        notifications.notifyAll(user.organizationId(), approvers, user.userId(), NotificationType.PURCHASE_ORDER_STATUS,
                "%s awaiting approval".formatted(po.getNumber()),
                "%s · %s".formatted(po.getSupplier().getName(), po.getTotalAmount()), link(po));
        return toResponse(user, po);
    }

    @Transactional
    public PurchaseOrderResponse approve(CurrentUser user, UUID id, StatusChangeRequest request) {
        PurchaseOrder po = load(user, id);
        OptimisticLock.check(request.version(), po.getVersion(), "Purchase order");
        assertCanApprove(user, po);
        po.markApproved(users.getReferenceById(user.userId()), clock.instant());
        recordStatus(user, po, PurchaseOrderStatus.SUBMITTED, null);
        notifyCreator(user, po, "%s approved".formatted(po.getNumber()));
        return toResponse(user, po);
    }

    @Transactional
    public PurchaseOrderResponse returnToDraft(CurrentUser user, UUID id, StatusChangeRequest request) {
        PurchaseOrder po = load(user, id);
        OptimisticLock.check(request.version(), po.getVersion(), "Purchase order");
        assertCanApprove(user, po);
        requireReason(request);
        po.transitionTo(PurchaseOrderStatus.DRAFT, request.reason().trim());
        recordStatus(user, po, PurchaseOrderStatus.SUBMITTED, request.reason());
        notifyCreator(user, po, "%s returned for changes".formatted(po.getNumber()));
        return toResponse(user, po);
    }

    /** Cancelling is allowed only before any goods arrived; after that the order is closed instead. */
    @Transactional
    public PurchaseOrderResponse cancel(CurrentUser user, UUID id, StatusChangeRequest request) {
        PurchaseOrder po = load(user, id);
        OptimisticLock.check(request.version(), po.getVersion(), "Purchase order");
        boolean creator = po.getCreatedBy().getId().equals(user.userId());
        if (!creator && !canApproveRole(user, po)) {
            throw new ForbiddenException("Only the creator or an approver can cancel this purchase order.");
        }
        if (po.hasReceivedAnything()) {
            throw new ConflictException("GOODS_ALREADY_RECEIVED", "Goods have been received; close the order instead.");
        }
        requireReason(request);
        PurchaseOrderStatus from = po.getStatus();
        po.transitionTo(PurchaseOrderStatus.CANCELLED, request.reason().trim());
        recordStatus(user, po, from, request.reason());
        return toResponse(user, po);
    }

    @Transactional
    public PurchaseOrderResponse close(CurrentUser user, UUID id, StatusChangeRequest request) {
        PurchaseOrder po = load(user, id);
        OptimisticLock.check(request.version(), po.getVersion(), "Purchase order");
        if (!canApproveRole(user, po)) {
            throw new ForbiddenException("Only an approver can close a purchase order.");
        }
        requireReason(request);
        po.transitionTo(PurchaseOrderStatus.CLOSED, request.reason().trim());
        recordStatus(user, po, PurchaseOrderStatus.PARTIALLY_RECEIVED, request.reason());
        return toResponse(user, po);
    }

    /**
     * Books a delivery (ADR-0006). Order of operations matters:
     * <ol>
     *   <li>Lock the purchase order row: concurrent receipts for the same order run one at a time.</li>
     *   <li>Then look up the idempotency key: a retry that waited on the lock now sees the committed
     *       original and returns it instead of booking stock again.</li>
     *   <li>Validate status, delivery note uniqueness and outstanding quantities.</li>
     *   <li>Write receipt, line quantities, stock movements, status and audit in this one transaction.</li>
     * </ol>
     */
    @Transactional
    public ReceiveResult receive(CurrentUser user, UUID id, String idempotencyKey, ReceiveRequest request) {
        PurchaseOrder po = orders.findForUpdate(id, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Purchase order"));
        String requestHash = hash(request);

        var existing = receipts.findByOrganizationIdAndIdempotencyKey(user.organizationId(), idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)
                    || !existing.get().getPurchaseOrder().getId().equals(po.getId())) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for a different request.");
            }
            return new ReceiveResult(toReceiptResponse(existing.get()), false);
        }
        if (!po.getStatus().canReceive()) {
            throw new ConflictException("NOT_RECEIVABLE", "Purchase order %s is %s and cannot receive goods."
                    .formatted(po.getNumber(), po.getStatus()));
        }
        String deliveryNote = request.deliveryNoteRef().trim();
        if (receipts.existsByPurchaseOrderIdAndDeliveryNoteRefIgnoreCase(po.getId(), deliveryNote)) {
            throw new ConflictException("DUPLICATE_DELIVERY_NOTE",
                    "Delivery note %s has already been booked against %s.".formatted(deliveryNote, po.getNumber()));
        }

        Map<UUID, PurchaseOrderItem> lines = po.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        GoodsReceipt receipt = new GoodsReceipt(po, deliveryNote, idempotencyKey, requestHash,
                users.getReferenceById(user.userId()), blank(request.notes()));
        for (ReceiveLine line : request.items()) {
            PurchaseOrderItem item = lines.get(line.purchaseOrderItemId());
            if (item == null) {
                throw new BadRequestException("UNKNOWN_LINE", "items", "A line does not belong to this purchase order.");
            }
            if (!seen.add(item.getId())) {
                throw new BadRequestException("DUPLICATE_LINE", "items", "Each order line may appear only once.");
            }
            item.receive(line.quantity());
            receipt.addItem(new GoodsReceiptItem(item, line.quantity()));
        }
        receipt = receipts.save(receipt);
        for (GoodsReceiptItem line : receipt.getItems()) {
            inventory.receiveFromPurchaseOrder(user, line.getPurchaseOrderItem().getMaterial().getId(),
                    line.getQuantity(), receipt.getId(), po.getNumber());
        }
        PurchaseOrderStatus from = po.getStatus();
        po.refreshReceivingStatus();
        orders.flush();
        auditService.record(user, AuditAction.GOODS_RECEIVED, "PurchaseOrder", po.getId(),
                po.getProject() != null ? po.getProject().getId() : null,
                "Received delivery %s against %s (%s → %s)".formatted(deliveryNote, po.getNumber(), from, po.getStatus()),
                Map.of("deliveryNoteRef", deliveryNote, "lines", receipt.getItems().size()));
        notifyCreator(user, po, "Goods received for %s (%s)".formatted(po.getNumber(), po.getStatus()));
        return new ReceiveResult(toReceiptResponse(receipt), true);
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> receipts(CurrentUser user, UUID id) {
        PurchaseOrder po = load(user, id);
        return receipts.findByPurchaseOrderIdOrderByReceivedAtAsc(po.getId()).stream()
                .map(PurchaseOrderService::toReceiptResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<PurchaseOrderStatus, Long> statusCounts(UUID organizationId) {
        return orders.countByStatus(organizationId).stream()
                .collect(Collectors.toMap(PurchaseOrderRepository.StatusCount::getStatus,
                        PurchaseOrderRepository.StatusCount::getTotal));
    }

    // ---- rules -------------------------------------------------------------------------------

    private void assertCanApprove(CurrentUser user, PurchaseOrder po) {
        if (po.getCreatedBy().getId().equals(user.userId())) {
            throw new ForbiddenException("SELF_APPROVAL", "You cannot approve a purchase order you created.");
        }
        if (!canApproveRole(user, po)) {
            throw new ForbiddenException("You cannot approve this purchase order.");
        }
    }

    /** Admins and finance officers approve any order; project managers only orders for projects they manage. */
    private boolean canApproveRole(CurrentUser user, PurchaseOrder po) {
        if (!user.has(Permission.PO_APPROVE)) {
            return false;
        }
        if (user.isAdmin() || user.role() == Role.FINANCE_OFFICER) {
            return true;
        }
        return user.role() == Role.PROJECT_MANAGER && po.getProject() != null
                && projectAccess.isProjectManager(user, po.getProject());
    }

    private Project resolveProject(CurrentUser user, UUID projectId) {
        if (projectId == null) {
            if (user.role() == Role.PROJECT_MANAGER) {
                throw new BadRequestException("PROJECT_REQUIRED", "projectId", "Project managers order for a specific project.");
            }
            return null;
        }
        Project project = projectAccess.requireReadable(user, projectId);
        projectAccess.requireOpen(project);
        if (user.role() == Role.PROJECT_MANAGER && !projectAccess.isProjectManager(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER", "You can only order for projects you manage.");
        }
        return project;
    }

    private Supplier activeSupplier(CurrentUser user, UUID supplierId) {
        Supplier supplier = suppliers.load(user.organizationId(), supplierId);
        if (!supplier.isActive()) {
            throw new ConflictException("SUPPLIER_INACTIVE", "This supplier is inactive.");
        }
        return supplier;
    }

    private List<PurchaseOrderItem> buildItems(CurrentUser user, List<PurchaseOrderLine> lines) {
        Set<UUID> seen = new HashSet<>();
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (PurchaseOrderLine line : lines) {
            if (!seen.add(line.materialId())) {
                throw new BadRequestException("DUPLICATE_MATERIAL", "items", "Each material may appear only once.");
            }
            Material material = materials.load(user, line.materialId());
            if (!material.isActive()) {
                throw new ConflictException("MATERIAL_INACTIVE", "Material %s is inactive.".formatted(material.getCode()));
            }
            items.add(new PurchaseOrderItem(material, line.quantity(), line.unitPrice()));
        }
        return items;
    }

    private PurchaseOrder load(CurrentUser user, UUID id) {
        return orders.findByIdAndOrganizationId(id, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Purchase order"));
    }

    private static void requireCreator(CurrentUser user, PurchaseOrder po) {
        if (!po.getCreatedBy().getId().equals(user.userId())) {
            throw new ForbiddenException("Only the creator can do this.");
        }
    }

    private static void requireReason(StatusChangeRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BadRequestException("REASON_REQUIRED", "reason", "A reason is required.");
        }
    }

    private void recordStatus(CurrentUser user, PurchaseOrder po, PurchaseOrderStatus from, String reason) {
        orders.flush();
        auditService.record(user, AuditAction.PURCHASE_ORDER_STATUS_CHANGED, "PurchaseOrder", po.getId(),
                po.getProject() != null ? po.getProject().getId() : null,
                "%s: %s → %s".formatted(po.getNumber(), from, po.getStatus()),
                reason == null ? Map.of("before", from, "after", po.getStatus())
                        : Map.of("before", from, "after", po.getStatus(), "reason", reason));
    }

    private void notifyCreator(CurrentUser user, PurchaseOrder po, String title) {
        if (!po.getCreatedBy().getId().equals(user.userId())) {
            notifications.notify(user.organizationId(), po.getCreatedBy().getId(), NotificationType.PURCHASE_ORDER_STATUS,
                    title, po.getSupplier().getName(), link(po), null);
        }
    }

    private static String link(PurchaseOrder po) {
        return "/procurement/" + po.getId();
    }

    /** Canonical form of the request (lines sorted) so semantically identical retries hash identically. */
    static String hash(ReceiveRequest request) {
        StringBuilder canonical = new StringBuilder(request.deliveryNoteRef().trim().toLowerCase()).append('|')
                .append(request.notes() == null ? "" : request.notes().trim()).append('|');
        request.items().stream()
                .sorted(Comparator.comparing(l -> l.purchaseOrderItemId().toString()))
                .forEach(l -> canonical.append(l.purchaseOrderItemId()).append(':')
                        .append(l.quantity().stripTrailingZeros().toPlainString()).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private PurchaseOrderResponse toResponse(CurrentUser user, PurchaseOrder po) {
        boolean creator = po.getCreatedBy().getId().equals(user.userId());
        boolean approver = !creator && canApproveRole(user, po);
        PurchaseOrderStatus s = po.getStatus();
        PurchaseOrderActions actions = new PurchaseOrderActions(
                creator && s == PurchaseOrderStatus.DRAFT,
                creator && s == PurchaseOrderStatus.DRAFT,
                approver && s == PurchaseOrderStatus.SUBMITTED,
                approver && s == PurchaseOrderStatus.SUBMITTED,
                user.has(Permission.PO_RECEIVE) && s.canReceive(),
                (creator || canApproveRole(user, po)) && s.allowedTargets().contains(PurchaseOrderStatus.CANCELLED)
                        && !po.hasReceivedAnything(),
                canApproveRole(user, po) && s == PurchaseOrderStatus.PARTIALLY_RECEIVED);
        List<PurchaseOrderItemResponse> items = po.getItems().stream()
                .map(i -> new PurchaseOrderItemResponse(i.getId(),
                        new MaterialRef(i.getMaterial().getId(), i.getMaterial().getCode(), i.getMaterial().getName(),
                                i.getMaterial().getUnit()),
                        i.getQuantityOrdered(), i.getQuantityReceived(), i.remaining(), i.getUnitPrice(), i.getLineTotal()))
                .toList();
        return new PurchaseOrderResponse(po.getId(), po.getNumber(),
                new Ref(po.getSupplier().getId(), null, po.getSupplier().getName()), projectRef(po), s,
                po.getStatusReason(), po.getExpectedDate(), po.getNotes(), po.getTotalAmount(),
                UserSummary.of(po.getCreatedBy()), UserSummary.of(po.getApprovedBy()), po.getApprovedAt(), items,
                actions, po.getVersion(), po.getCreatedAt());
    }

    private static PurchaseOrderSummary toSummary(PurchaseOrder po) {
        return new PurchaseOrderSummary(po.getId(), po.getNumber(),
                new Ref(po.getSupplier().getId(), null, po.getSupplier().getName()), projectRef(po), po.getStatus(),
                po.getExpectedDate(), po.getTotalAmount(), UserSummary.of(po.getCreatedBy()), po.getCreatedAt());
    }

    private static Ref projectRef(PurchaseOrder po) {
        Project p = po.getProject();
        return p == null ? null : new Ref(p.getId(), p.getCode(), p.getName());
    }

    private static GoodsReceiptResponse toReceiptResponse(GoodsReceipt r) {
        return new GoodsReceiptResponse(r.getId(), r.getPurchaseOrder().getId(), r.getDeliveryNoteRef(),
                UserSummary.of(r.getReceivedBy()), r.getReceivedAt(), r.getNotes(),
                r.getItems().stream().map(i -> new GoodsReceiptLineResponse(i.getPurchaseOrderItem().getId(),
                        i.getPurchaseOrderItem().getMaterial().getCode(), i.getPurchaseOrderItem().getMaterial().getName(),
                        i.getQuantity())).toList());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ReceiveResult(GoodsReceiptResponse receipt, boolean created) {
    }
}
