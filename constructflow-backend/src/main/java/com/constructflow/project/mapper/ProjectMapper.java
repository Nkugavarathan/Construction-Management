package com.constructflow.project.mapper;

import com.constructflow.finance.service.ExpenseService;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.user.dto.UserSummary;

import java.math.BigDecimal;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    /** Spend and utilization are null when the caller may not see the project's finances. */
    public static ProjectSummaryResponse toSummary(Project p, int progress, BigDecimal approvedSpend) {
        return new ProjectSummaryResponse(p.getId(), p.getCode(), p.getName(), p.getClientName(), p.getStatus(),
                UserSummary.of(p.getManager()), p.getStartDate(), p.getPlannedEndDate(), p.getApprovedBudget(),
                approvedSpend, approvedSpend == null ? null : ExpenseService.percent(approvedSpend, p.getApprovedBudget()),
                progress);
    }

    public static ProjectResponse toResponse(Project p, int progress, ProjectRole myRole, boolean canManage,
                                             boolean canViewFinance) {
        return new ProjectResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getClientName(),
                p.getClientContact(), p.getSiteAddress(), p.getStartDate(), p.getPlannedEndDate(),
                p.getActualEndDate(), p.getApprovedBudget(), p.getStatus(), p.getStatusReason(),
                canManage ? p.getStatus().allowedTargets() : java.util.Set.of(),
                UserSummary.of(p.getManager()), progress, myRole, canManage, canViewFinance, p.getVersion(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
