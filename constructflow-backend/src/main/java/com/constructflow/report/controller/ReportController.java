package com.constructflow.report.controller;

import com.constructflow.report.dto.ReportDtos.DashboardResponse;
import com.constructflow.report.dto.ReportDtos.ProjectReportResponse;
import com.constructflow.report.service.ReportService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dashboard & reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    public DashboardResponse dashboard(@AuthenticationPrincipal CurrentUser user) {
        return reportService.dashboard(user);
    }

    @GetMapping("/reports/projects/{projectId}")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ProjectReportResponse projectReport(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        return reportService.projectReport(user, projectId);
    }
}
