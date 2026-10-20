package com.constructflow.report.dto;

import com.constructflow.finance.dto.FinanceDtos.CategorySummary;
import com.constructflow.finance.dto.FinanceDtos.FinancialSummaryResponse;
import com.constructflow.inventory.dto.InventoryDtos.ProjectMaterialUsage;
import com.constructflow.procurement.entity.PurchaseOrderStatus;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.risk.dto.RiskDtos.RiskResponse;
import com.constructflow.risk.entity.Risk;
import com.constructflow.task.dto.TaskDtos.MilestoneResponse;
import com.constructflow.task.dto.TaskDtos.TaskResponse;
import com.constructflow.task.dto.TaskDtos.UpcomingMilestone;
import com.constructflow.task.entity.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** Finance block is null when the user may not see financial data; same for inventory and procurement. */
    public record DashboardResponse(Instant generatedAt, String currencyCode, Map<ProjectStatus, Long> projectsByStatus,
                                   long overdueTasks, long myOpenTasks, long pendingApprovals, long openIncidents,
                                   Map<Risk.Level, Long> openRisksByLevel, List<UpcomingMilestone> upcomingMilestones,
                                   List<ProjectSummaryResponse> activeProjects, FinanceBlock finance,
                                   Long lowStockMaterials, Map<PurchaseOrderStatus, Long> purchaseOrdersByStatus) {
    }

    public record FinanceBlock(BigDecimal activeBudget, BigDecimal approvedSpend, BigDecimal pendingSpend,
                               BigDecimal utilizationPercent, List<CategorySummary> spendByCategory) {
    }

    public record ProjectReportResponse(Instant generatedAt, ProjectResponse project,
                                        Map<TaskStatus, Long> tasksByStatus, List<TaskResponse> overdueTasks,
                                        List<MilestoneResponse> milestones, FinancialSummaryResponse finance,
                                        List<RiskResponse> topRisks, long openIncidents,
                                        List<ProjectMaterialUsage> materialUsage) {
    }
}
