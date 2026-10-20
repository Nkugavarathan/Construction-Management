package com.constructflow.finance.repository;

import com.constructflow.finance.entity.ExpenseApproval;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseApprovalRepository extends JpaRepository<ExpenseApproval, UUID> {

    @EntityGraph(attributePaths = "actor")
    List<ExpenseApproval> findByExpenseIdOrderByCreatedAtAsc(UUID expenseId);
}
