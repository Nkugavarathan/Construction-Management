package com.constructflow.task.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.security.CurrentUser;
import com.constructflow.task.dto.TaskDtos.MilestoneRequest;
import com.constructflow.task.dto.TaskDtos.MilestoneResponse;
import com.constructflow.task.entity.Milestone;
import com.constructflow.task.repository.MilestoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MilestoneService {

    private final MilestoneRepository milestones;
    private final TaskService tasks;
    private final ProjectAccessPolicy access;
    private final OrganizationService organizations;
    private final AuditService auditService;
    private final Clock clock;

    public MilestoneService(MilestoneRepository milestones, TaskService tasks, ProjectAccessPolicy access,
                            OrganizationService organizations, AuditService auditService, Clock clock) {
        this.milestones = milestones;
        this.tasks = tasks;
        this.access = access;
        this.organizations = organizations;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(CurrentUser user, UUID projectId) {
        access.requireReadable(user, projectId);
        List<Milestone> list = milestones.findByProjectIdOrderByDueDateAsc(projectId);
        Map<UUID, Integer> progress = tasks.progressByMilestone(list.stream().map(Milestone::getId).toList());
        LocalDate today = organizations.today(user.organizationId());
        return list.stream().map(m -> toResponse(m, progress.getOrDefault(m.getId(), 0), today)).toList();
    }

    @Transactional
    public MilestoneResponse create(CurrentUser user, UUID projectId, MilestoneRequest request) {
        Project project = access.requireManageable(user, projectId);
        access.requireOpen(project);
        Milestone milestone = milestones.save(new Milestone(project, request.name().trim(),
                blankToNull(request.description()), request.dueDate()));
        auditService.record(user, AuditAction.MILESTONE_CREATED, "Milestone", milestone.getId(), project.getId(),
                "Created milestone '%s' due %s".formatted(milestone.getName(), milestone.getDueDate()), Map.of());
        return toResponse(milestone, 0, organizations.today(user.organizationId()));
    }

    /** A milestone can be completed only when none of its tasks are still open. */
    @Transactional
    public MilestoneResponse update(CurrentUser user, UUID milestoneId, MilestoneRequest request) {
        Milestone milestone = milestones.findInOrganization(milestoneId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Milestone"));
        Project project = access.requireReadable(user, milestone.getProject().getId());
        if (!access.canManage(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER", "Only the project's managers can edit milestones.");
        }
        access.requireOpen(project);
        OptimisticLock.check(request.version(), milestone.getVersion(), "Milestone");
        milestone.update(request.name().trim(), blankToNull(request.description()), request.dueDate());
        if (request.status() == Milestone.Status.COMPLETED && milestone.getStatus() != Milestone.Status.COMPLETED) {
            long open = tasks.countOpenInMilestone(milestoneId);
            if (open > 0) {
                throw new ConflictException("MILESTONE_HAS_OPEN_TASKS",
                        "Finish or cancel the %d open task(s) in this milestone first.".formatted(open));
            }
            milestone.complete(clock.instant());
        } else if (request.status() == Milestone.Status.PENDING && milestone.getStatus() == Milestone.Status.COMPLETED) {
            milestone.reopen();
        }
        milestones.flush();
        auditService.record(user, AuditAction.MILESTONE_UPDATED, "Milestone", milestone.getId(), project.getId(),
                "Updated milestone '%s' (%s)".formatted(milestone.getName(), milestone.getStatus()), Map.of());
        int progress = tasks.progressByMilestone(List.of(milestoneId)).getOrDefault(milestoneId, 0);
        return toResponse(milestone, progress, organizations.today(user.organizationId()));
    }

    @Transactional(readOnly = true)
    public List<Milestone> upcoming(Collection<UUID> projectIds, LocalDate until) {
        return projectIds.isEmpty() ? List.of() : milestones.findPendingDueBy(projectIds, until);
    }

    private static MilestoneResponse toResponse(Milestone m, int progress, LocalDate today) {
        boolean overdue = m.getStatus() == Milestone.Status.PENDING && m.getDueDate().isBefore(today);
        return new MilestoneResponse(m.getId(), m.getProject().getId(), m.getName(), m.getDescription(), m.getDueDate(),
                m.getStatus(), m.getCompletedAt(), progress, overdue, m.getVersion());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
