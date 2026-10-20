package com.constructflow.procurement.repository;

import com.constructflow.procurement.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    @Query("""
            select s from Supplier s where s.organizationId = :organizationId
              and (:q is null or lower(s.name) like :q)
              and (:includeInactive = true or s.active = true)
            """)
    Page<Supplier> search(@Param("organizationId") UUID organizationId, @Param("q") String q,
                          @Param("includeInactive") boolean includeInactive, Pageable pageable);
}
