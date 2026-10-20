package com.constructflow.task.service;

import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.response.PageResponse;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.security.CurrentUser;
import com.constructflow.task.dto.TaskDtos.SiteReportRequest;
import com.constructflow.task.dto.TaskDtos.SiteReportResponse;
import com.constructflow.task.entity.SiteReport;
import com.constructflow.task.repository.SiteReportRepository;
import com.constructflow.user.dto.UserSummary;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SiteReportService {

    private final SiteReportRepository reports;
    private final ProjectAccessPolicy access;
    private final OrganizationService organizations;
    private final UserRepository users;

    public SiteReportService(SiteReportRepository reports, ProjectAccessPolicy access,
                             OrganizationService organizations, UserRepository users) {
        this.reports = reports;
        this.access = access;
        this.organizations = organizations;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<SiteReportResponse> list(CurrentUser user, UUID projectId, Pageable pageable) {
        access.requireReadable(user, projectId);
        return PageResponse.of(reports.findByProjectId(projectId, pageable), SiteReportService::toResponse);
    }

    /** Only people working on the project write its diary; one entry per author per day; entries are immutable. */
    @Transactional
    public SiteReportResponse create(CurrentUser user, UUID projectId, SiteReportRequest request) {
        Project project = access.requireReadable(user, projectId);
        if (!user.isAdmin() && !access.isMember(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MEMBER", "Only project members can submit site reports.");
        }
        access.requireOpen(project);
        if (request.reportDate().isAfter(organizations.today(user.organizationId()))) {
            throw new BadRequestException("FUTURE_DATE", "reportDate", "Report date cannot be in the future.");
        }
        if (reports.existsByProjectIdAndAuthorIdAndReportDate(projectId, user.userId(), request.reportDate())) {
            throw new ConflictException("DUPLICATE_SITE_REPORT", "You already submitted a report for this date.");
        }
        SiteReport report = reports.save(new SiteReport(project, users.getReferenceById(user.userId()),
                request.reportDate(), blankToNull(request.weather()), request.workforceCount(),
                request.workPerformed().trim(), blankToNull(request.issues())));
        reports.flush();
        return new SiteReportResponse(report.getId(), projectId,
                new UserSummary(user.userId(), user.fullName(), user.email()), report.getReportDate(),
                report.getWeather(), report.getWorkforceCount(), report.getWorkPerformed(), report.getIssues(),
                report.getCreatedAt());
    }

    private static SiteReportResponse toResponse(SiteReport r) {
        return new SiteReportResponse(r.getId(), r.getProject().getId(), UserSummary.of(r.getAuthor()), r.getReportDate(),
                r.getWeather(), r.getWorkforceCount(), r.getWorkPerformed(), r.getIssues(), r.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
