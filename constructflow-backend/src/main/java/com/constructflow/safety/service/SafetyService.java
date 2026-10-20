package com.constructflow.safety.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.safety.dto.SafetyDtos.IncidentResponse;
import com.constructflow.safety.dto.SafetyDtos.ReportIncidentRequest;
import com.constructflow.safety.dto.SafetyDtos.UpdateIncidentRequest;
import com.constructflow.safety.entity.SafetyIncident;
import com.constructflow.safety.repository.SafetyIncidentRepository;
import com.constructflow.security.CurrentUser;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
public class SafetyService {

    private final SafetyIncidentRepository incidents;
    private final ProjectAccessPolicy access;
    private final ProjectMemberService projectMembers;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final UserRepository users;
    private final Clock clock;

    public SafetyService(SafetyIncidentRepository incidents, ProjectAccessPolicy access,
                         ProjectMemberService projectMembers, NotificationService notifications,
                         AuditService auditService, UserRepository users, Clock clock) {
        this.incidents = incidents;
        this.access = access;
        this.projectMembers = projectMembers;
        this.notifications = notifications;
        this.auditService = auditService;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(CurrentUser user, UUID projectId, SafetyIncident.Status status,
                                               SafetyIncident.Severity severity, Pageable pageable) {
        access.requireReadable(user, projectId);
        return PageResponse.of(incidents.search(projectId, status, severity, pageable), IncidentResponse::of);
    }

    /** Anyone who can see the project and holds INCIDENT_REPORT may report: safety reporting should be easy. */
    @Transactional
    public IncidentResponse report(CurrentUser user, UUID projectId, ReportIncidentRequest request) {
        Project project = access.requireReadable(user, projectId);
        SafetyIncident incident = incidents.save(new SafetyIncident(project, request.occurredAt(),
                request.location().trim(), request.severity(), request.description().trim(),
                users.getReferenceById(user.userId())));
        auditService.record(user, AuditAction.INCIDENT_REPORTED, "SafetyIncident", incident.getId(), projectId,
                "%s incident reported at %s".formatted(request.severity(), request.location().trim()),
                Map.of("severity", request.severity()));
        notifications.notifyAll(user.organizationId(), projectMembers.userIdsWithRole(projectId, ProjectRole.MANAGER),
                user.userId(), NotificationType.INCIDENT_REPORTED,
                "%s safety incident on %s".formatted(request.severity(), project.getCode()), request.location(),
                "/projects/%s/safety".formatted(projectId));
        incidents.flush();
        return IncidentResponse.of(incident);
    }

    @Transactional
    public IncidentResponse update(CurrentUser user, UUID incidentId, UpdateIncidentRequest request) {
        SafetyIncident incident = incidents.findInOrganization(incidentId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Incident"));
        Project project = access.requireReadable(user, incident.getProject().getId());
        if (!access.canManage(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER", "Only the project's managers can investigate incidents.");
        }
        OptimisticLock.check(request.version(), incident.getVersion(), "Incident");
        SafetyIncident.Status before = incident.getStatus();
        incident.updateInvestigation(request.severity(),
                request.correctiveActions() == null || request.correctiveActions().isBlank() ? null : request.correctiveActions().trim());
        if (request.status() != null && request.status() != incident.getStatus()) {
            incident.transitionTo(request.status(), users.getReferenceById(user.userId()), clock.instant());
            auditService.record(user, AuditAction.INCIDENT_STATUS_CHANGED, "SafetyIncident", incident.getId(),
                    project.getId(), "Incident %s → %s".formatted(before, incident.getStatus()),
                    Map.of("before", before, "after", incident.getStatus()));
        }
        incidents.flush();
        return IncidentResponse.of(incident);
    }

    @Transactional(readOnly = true)
    public long countOpen(Collection<UUID> projectIds) {
        return projectIds.isEmpty() ? 0 : incidents.countOpen(projectIds);
    }
}
