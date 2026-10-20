package com.constructflow.finance.repository;

import com.constructflow.finance.entity.BudgetAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, UUID> {

    @EntityGraph(attributePaths = "category")
    List<BudgetAllocation> findByProjectId(UUID projectId);

    @Query("select coalesce(sum(a.amount), 0) from BudgetAllocation a where a.project.id = :projectId")
    BigDecimal totalAllocated(@Param("projectId") UUID projectId);
}
