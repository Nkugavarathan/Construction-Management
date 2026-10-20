package com.constructflow.finance.repository;

import com.constructflow.finance.entity.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, UUID> {

    List<BudgetCategory> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<BudgetCategory> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}
