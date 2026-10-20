package com.constructflow.report.service;

import com.constructflow.finance.dto.FinanceDtos.FinancialSummaryResponse;
import com.constructflow.finance.entity.ExpenseStatus;
import com.constructflow.finance.service.ExpenseService;
import com.constructflow.inventory.service.InventoryService;
import com.constructflow.inventory.service.MaterialService;
import com.constructflow.organization.entity.Organization;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.procurement.service.PurchaseOrderService;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.project.service.ProjectService;
import com.constructflow.report.dto.ReportDtos.DashboardResponse;
import com.constructflow.report.dto.ReportDtos.FinanceBlock;
import com.constructflow.report.dto.ReportDtos.ProjectReportResponse;
import com.constructflow.risk.service.RiskService;
import com.constructflow.safety.service.SafetyService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Permission;
import com.constructflow.task.service.MilestoneService;
import com.constructflow.task.service.TaskService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Read-only analytics assembled from the other modules' services. Every number is computed by a
 * query and scoped to the projects the user may see; nothing is hard-coded. Each metric is a single
 * grouped query over the visible project IDs, so the cost does not grow with the number of projects
 * shown on screen.
 */
@Service
public class ReportService {

    private final ProjectAccessPolicy access;
    private final ProjectService projects;
    private final TaskService tasks;
    private final MilestoneService milestones;
    private final ExpenseService expenses;
    private final MaterialService materials;
    private final InventoryService inventory;
    private final PurchaseOrderService purchaseOrders;
    private final RiskService risks;
    private final SafetyService safety;
    private final OrganizationService organizations;
    private final Clock clock;

    public ReportService(ProjectAccessPolicy access, ProjectService projects, TaskService tasks,
                         MilestoneService milestones, ExpenseService expenses, MaterialService materials,
                         InventoryService inventory, PurchaseOrderService purchaseOrders, RiskService risks,
                         SafetyService safety, OrganizationService organizations, Clock clock) {
        this.access = access;
        this.projects = projects;
        this.tasks = tasks;
        this.milestones = milestones;
        this.expenses = expenses;
        this.materials = materials;
        this.inventory = inventory;
        this.purchaseOrders = purchaseOrders;
        this.risks = risks;
        this.safety = safety;
        this.organizations = organizations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(CurrentUser user) {
        Organization organization = organizations.load(user.organizationId());
        LocalDate today = organizations.today(user.organizationId());
        List<UUID> visible = access.visibleProjectIds(user);

        List<ProjectSummaryResponse> active = projects.activeSummaries(user, visible);
        FinanceBlock finance = null;
        List<UUID> financeIds = projects.financeVisibleIds(user, visible);
        if (!financeIds.isEmpty()) {
            List<UUID> activeFinanceIds = active.stream().map(ProjectSummaryResponse::id).filter(financeIds::contains).toList();
            BigDecimal budget = active.stream().filter(p -> activeFinanceIds.contains(p.id()))
                    .map(ProjectSummaryResponse::approvedBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal approved = expenses.totalForProjects(activeFinanceIds, ExpenseStatus.APPROVED);
            BigDecimal pending = expenses.totalForProjects(activeFinanceIds, ExpenseStatus.SUBMITTED);
            finance = new FinanceBlock(budget, approved, pending, ExpenseService.percent(approved, budget),
                    expenses.approvedByCategory(financeIds));
        }

        return new DashboardResponse(clock.instant(), organization.getCurrencyCode(),
                projects.statusCounts(visible),
                tasks.countOverdue(visible, today),
                tasks.countOpenAssignedTo(user),
                expenses.countDecidable(user),
                user.has(Permission.INCIDENT_READ) ? safety.countOpen(visible) : 0,
                risks.openRiskDistribution(user.has(Permission.RISK_READ) ? visible : List.of()),
                milestones.upcoming(visible, today.plusDays(14), 8),
                active.stream().sorted(Comparator.comparing(ProjectSummaryResponse::plannedEndDate)).limit(8).toList(),
                finance,
                user.has(Permission.INVENTORY_READ) ? materials.countLowStock(user.organizationId()) : null,
                user.has(Permission.PROCUREMENT_READ) ? purchaseOrders.statusCounts(user.organizationId()) : null);
    }

    /** Printable status report for one project: progress, schedule, money, risks, safety and materials. */
    @Transactional(readOnly = true)
    public ProjectReportResponse projectReport(CurrentUser user, UUID projectId) {
        ProjectResponse project = projects.get(user, projectId);
        FinancialSummaryResponse finance = project.canViewFinance() ? expenses.financialSummary(user, projectId) : null;
        return new ProjectReportResponse(clock.instant(), project,
                tasks.statusCounts(projectId),
                tasks.search(user, projectId, null, null, null, true, null,
                        PageRequest.of(0, 20, Sort.by("dueDate"))).content(),
                milestones.list(user, projectId),
                finance,
                user.has(Permission.RISK_READ) ? risks.topOpen(projectId, 5) : List.of(),
                user.has(Permission.INCIDENT_READ) ? safety.countOpen(List.of(projectId)) : 0,
                inventory.usageForProject(projectId));
    }
}
