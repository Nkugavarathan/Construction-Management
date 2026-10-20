package com.constructflow.inventory.repository;

import com.constructflow.inventory.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    @EntityGraph(attributePaths = {"project", "actor"})
    Page<InventoryMovement> findByMaterialId(UUID materialId, Pageable pageable);

    /** Reconciliation: the ledger total must always equal the cached balance. */
    @Query("select coalesce(sum(m.quantity), 0) from InventoryMovement m where m.material.id = :materialId")
    BigDecimal ledgerBalance(@Param("materialId") UUID materialId);

    /** Net quantity issued to a project per material (issues are negative, returns positive). */
    @Query("""
            select m.material.id as materialId, m.material.code as code, m.material.name as name,
                   m.material.unit as unit, -sum(m.quantity) as quantity
            from InventoryMovement m
            where m.project.id = :projectId and m.type in (com.constructflow.inventory.entity.MovementType.ISSUE,
                                                           com.constructflow.inventory.entity.MovementType.RETURN)
            group by m.material.id, m.material.code, m.material.name, m.material.unit
            order by m.material.code
            """)
    List<ProjectUsage> usageByProject(@Param("projectId") UUID projectId);

    interface ProjectUsage {
        UUID getMaterialId();

        String getCode();

        String getName();

        com.constructflow.inventory.entity.MaterialUnit getUnit();

        BigDecimal getQuantity();
    }
}
