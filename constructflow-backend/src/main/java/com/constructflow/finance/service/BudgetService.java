package com.constructflow.finance.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.finance.dto.FinanceDtos.AllocationItem;
import com.constructflow.finance.dto.FinanceDtos.AllocationResponse;
import com.constructflow.finance.dto.FinanceDtos.BudgetAllocationsResponse;
import com.constructflow.finance.entity.BudgetAllocation;
import com.constructflow.finance.entity.BudgetCategory;
import com.constructflow.finance.repository.BudgetAllocationRepository;
import com.constructflow.project.entity.Project;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private final BudgetAllocationRepository allocations;
    private final BudgetCategoryService categories;
    private final ProjectAccessPolicy access;
    private final AuditService auditService;

    public BudgetService(BudgetAllocationRepository allocations, BudgetCategoryService categories,
                         ProjectAccessPolicy access, AuditService auditService) {
        this.allocations = allocations;
        this.categories = categories;
        this.access = access;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BudgetAllocationsResponse get(CurrentUser user, UUID projectId) {
        Project project = access.requireReadable(user, projectId);
        requireFinanceView(user, project);
        return toResponse(project, allocations.findByProjectId(projectId));
    }

    /**
     * Replaces the project's whole allocation set in one transaction. The rule "allocations may not exceed
     * the approved budget" is about the set, so partial updates would allow it to be broken in between.
     */
    @Transactional
    public BudgetAllocationsResponse replace(CurrentUser user, UUID projectId, List<AllocationItem> items) {
        Project project = access.requireReadable(user, projectId);
        boolean allowed = user.isAdmin() || user.role() == Role.FINANCE_OFFICER || access.canManage(user, project);
        if (!allowed) {
            throw new ForbiddenException("Only the project's managers or finance can change its budget.");
        }
        access.requireOpen(project);

        Set<UUID> seen = new HashSet<>();
        for (AllocationItem item : items) {
            if (!seen.add(item.categoryId())) {
                throw new BadRequestException("DUPLICATE_CATEGORY", "allocations", "Each category may appear only once.");
            }
        }
        BigDecimal total = items.stream().map(AllocationItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(project.getApprovedBudget()) > 0) {
            throw new BadRequestException("ALLOCATION_EXCEEDS_BUDGET", "allocations",
                    "Allocations (%s) exceed the approved budget (%s).".formatted(total, project.getApprovedBudget()));
        }

        List<BudgetAllocation> existing = allocations.findByProjectId(projectId);
        Map<String, BigDecimal> before = snapshot(existing);
        Map<UUID, BudgetAllocation> byCategory = existing.stream()
                .collect(Collectors.toMap(a -> a.getCategory().getId(), Function.identity()));

        for (AllocationItem item : items) {
            BudgetAllocation current = byCategory.remove(item.categoryId());
            if (current != null) {
                current.changeAmount(item.amount());
            } else {
                BudgetCategory category = categories.requireActive(user.organizationId(), item.categoryId());
                allocations.save(new BudgetAllocation(project, category, item.amount()));
            }
        }
        allocations.deleteAll(byCategory.values());
        allocations.flush();

        List<BudgetAllocation> updated = allocations.findByProjectId(projectId);
        auditService.record(user, AuditAction.BUDGET_ALLOCATION_CHANGED, "Project", project.getId(), project.getId(),
                "Budget allocations updated for " + project.getCode(),
                Map.of("before", before, "after", snapshot(updated)));
        return toResponse(project, updated);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAllocated(UUID projectId) {
        return allocations.totalAllocated(projectId);
    }

    @Transactional(readOnly = true)
    public List<BudgetAllocation> allocationsFor(UUID projectId) {
        return allocations.findByProjectId(projectId);
    }

    private void requireFinanceView(CurrentUser user, Project project) {
        if (!access.canViewFinance(user, project)) {
            throw new ForbiddenException("You cannot view this project's finances.");
        }
    }

    private static Map<String, BigDecimal> snapshot(List<BudgetAllocation> list) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        list.stream().sorted(Comparator.comparing(a -> a.getCategory().getName()))
                .forEach(a -> map.put(a.getCategory().getName(), a.getAmount()));
        return map;
    }

    private static BudgetAllocationsResponse toResponse(Project project, List<BudgetAllocation> list) {
        BigDecimal total = list.stream().map(BudgetAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AllocationResponse> items = list.stream()
                .sorted(Comparator.comparing(a -> a.getCategory().getName()))
                .map(a -> new AllocationResponse(a.getCategory().getId(), a.getCategory().getName(), a.getAmount()))
                .toList();
        return new BudgetAllocationsResponse(project.getId(), project.getApprovedBudget(), total,
                project.getApprovedBudget().subtract(total), items);
    }
}
