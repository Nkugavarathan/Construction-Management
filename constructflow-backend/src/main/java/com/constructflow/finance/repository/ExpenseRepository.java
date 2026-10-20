package com.constructflow.finance.repository;

import com.constructflow.finance.entity.Expense;
import com.constructflow.finance.entity.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = {"project", "category", "createdBy", "submittedBy", "decidedBy", "reversesExpense"})
    Optional<Expense> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Override
    @EntityGraph(attributePaths = {"project", "category", "createdBy", "submittedBy", "decidedBy"})
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    boolean existsByReversesExpenseId(UUID expenseId);

    long countByProjectIdAndStatus(UUID projectId, ExpenseStatus status);

    /** Sum of amounts per project for one status, in a single grouped query. */
    @Query("""
            select e.project.id as projectId, sum(e.amount) as total from Expense e
            where e.project.id in :projectIds and e.status = :status
            group by e.project.id
            """)
    List<ProjectTotal> totalsByProject(@Param("projectIds") Collection<UUID> projectIds,
                                       @Param("status") ExpenseStatus status);

    @Query("""
            select e.category.id as categoryId, e.category.name as categoryName, e.status as status, sum(e.amount) as total
            from Expense e
            where e.project.id in :projectIds and e.status in :statuses
            group by e.category.id, e.category.name, e.status
            """)
    List<CategoryTotal> totalsByCategory(@Param("projectIds") Collection<UUID> projectIds,
                                         @Param("statuses") Collection<ExpenseStatus> statuses);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.project.id in :projectIds and e.status = :status")
    BigDecimal total(@Param("projectIds") Collection<UUID> projectIds, @Param("status") ExpenseStatus status);

    interface ProjectTotal {
        UUID getProjectId();

        BigDecimal getTotal();
    }

    interface CategoryTotal {
        UUID getCategoryId();

        String getCategoryName();

        ExpenseStatus getStatus();

        BigDecimal getTotal();
    }
}
