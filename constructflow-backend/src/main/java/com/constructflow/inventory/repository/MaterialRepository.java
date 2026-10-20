package com.constructflow.inventory.repository;

import com.constructflow.inventory.entity.Material;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    Optional<Material> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * {@code SELECT ... FOR UPDATE}: concurrent stock changes for the same material wait here, so each
     * one reads the balance left by the previous one (ADR-0004).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = :id and m.organizationId = :organizationId")
    Optional<Material> findForUpdate(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCase(UUID organizationId, String code);

    @Query("""
            select m from Material m
            where m.organizationId = :organizationId
              and (:q is null or lower(m.name) like :q or lower(m.code) like :q)
              and (:lowStockOnly = false or m.quantityOnHand <= m.reorderLevel)
              and (:includeInactive = true or m.active = true)
            """)
    Page<Material> search(@Param("organizationId") UUID organizationId, @Param("q") String q,
                          @Param("lowStockOnly") boolean lowStockOnly,
                          @Param("includeInactive") boolean includeInactive, Pageable pageable);

    @Query("select count(m) from Material m where m.organizationId = :organizationId and m.active = true and m.quantityOnHand <= m.reorderLevel")
    long countLowStock(@Param("organizationId") UUID organizationId);
}
