package com.constructflow.procurement.repository;

import com.constructflow.procurement.entity.GoodsReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    Optional<GoodsReceipt> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    boolean existsByPurchaseOrderIdAndDeliveryNoteRefIgnoreCase(UUID purchaseOrderId, String deliveryNoteRef);

    @EntityGraph(attributePaths = {"receivedBy", "items", "items.purchaseOrderItem", "items.purchaseOrderItem.material"})
    List<GoodsReceipt> findByPurchaseOrderIdOrderByReceivedAtAsc(UUID purchaseOrderId);
}
