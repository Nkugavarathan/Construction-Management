package com.constructflow.procurement.repository;

import com.constructflow.procurement.entity.PurchaseOrder;
import com.constructflow.procurement.entity.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @EntityGraph(attributePaths = {"supplier", "project", "createdBy", "approvedBy"})
    Optional<PurchaseOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Receiving locks the purchase order so two deliveries cannot over-receive the same line concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id and po.organizationId = :organizationId")
    Optional<PurchaseOrder> findForUpdate(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = {"supplier", "project", "createdBy"})
    @Query("""
            select po from PurchaseOrder po where po.organizationId = :organizationId
              and (:status is null or po.status = :status)
              and (:supplierId is null or po.supplier.id = :supplierId)
              and (:projectId is null or po.project.id = :projectId)
            """)
    Page<PurchaseOrder> search(@Param("organizationId") UUID organizationId,
                               @Param("status") PurchaseOrderStatus status,
                               @Param("supplierId") UUID supplierId,
                               @Param("projectId") UUID projectId, Pageable pageable);

    @Query("select po.status as status, count(po) as total from PurchaseOrder po where po.organizationId = :organizationId group by po.status")
    List<StatusCount> countByStatus(@Param("organizationId") UUID organizationId);

    interface StatusCount {
        PurchaseOrderStatus getStatus();

        long getTotal();
    }
}
