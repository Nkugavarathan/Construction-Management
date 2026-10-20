package com.constructflow.task.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Permission;
import com.constructflow.security.Role;
import com.constructflow.task.dto.TaskDtos.MilestoneRef;
import com.constructflow.task.dto.TaskDtos.ProjectRef;
import com.constructflow.task.dto.TaskDtos.TaskRequest;
import com.constructflow.task.dto.TaskDtos.TaskResponse;
import com.constructflow.task.dto.TaskDtos.TaskTransitionRequest;
import com.constructflow.task.entity.Milestone;
import com.constructflow.task.entity.Task;
import com.constructflow.task.entity.TaskStatus;
import com.constructflow.task.repository.MilestoneRepository;
import com.constructflow.task.repository.TaskRepository;
import com.constructflow.user.dto.UserSummary;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository tasks;
    private final MilestoneRepository milestones;
    private final ProjectAccessPolicy access;
    private final OrganizationService organizations;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final UserRepository users;
    private final Clock clock;

    public TaskService(TaskRepository tasks, MilestoneRepository milestones, ProjectAccessPolicy access,
                       OrganizationService organizations, NotificationService notifications, AuditService auditService,
                       UserRepository users, Clock clock) {
        this.tasks = tasks;
        this.milestones = milestones;
        this.access = access;
        this.organizations = organizations;
        this.notifications = notifications;
        this.auditService = auditService;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> search(CurrentUser user, UUID projectId, TaskStatus status, UUID assigneeId,
                                             UUID milestoneId, boolean overdueOnly, String q, Pageable pageable) {
        access.requireReadable(user, projectId);
        LocalDate today = organizations.today(user.organizationId());
        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("project").get("id"), projectId));
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (assigneeId != null) {
                p.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (milestoneId != null) {
                p.add(cb.equal(root.get("milestone").get("id"), milestoneId));
            }
            if (overdueOnly) {
                p.add(cb.lessThan(root.get("dueDate"), today));
                p.add(root.get("status").in(TaskStatus.OPEN));
            }
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("title")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return toPage(user, tasks.findAll(spec, pageable), today);
    }

    /** "My tasks": open tasks assigned to the current user across the active organization. */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> mine(CurrentUser user, boolean includeClosed, Pageable pageable) {
        LocalDate today = organizations.today(user.organizationId());
        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("assignee").get("id"), user.userId()));
            p.add(cb.equal(root.get("project").get("organizationId"), user.organizationId()));
            if (!includeClosed) {
                p.add(root.get("status").in(TaskStatus.OPEN));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return toPage(user, tasks.findAll(spec, pageable), today);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(CurrentUser user, UUID taskId) {
        Task task = loadReadable(user, taskId);
        return toResponse(user, task, organizations.today(user.organizationId()), access.canManage(user, task.getProject()));
    }

    @Transactional
    public TaskResponse create(CurrentUser user, UUID projectId, TaskRequest request) {
        Project project = access.requireManageable(user, projectId);
        access.requireOpen(project);
        User assignee = resolveAssignee(project, request.assigneeId());
        Milestone milestone = resolveMilestone(user, project, request.milestoneId());
        Task task = tasks.save(new Task(project, milestone, request.title().trim(), blankToNull(request.description()),
                assignee, request.priority(), request.weight() != null ? request.weight() : 1, request.startDate(),
                request.dueDate(), users.getReferenceById(user.userId())));
        auditService.record(user, AuditAction.TASK_CREATED, "Task", task.getId(), project.getId(),
                "Created task '%s' in %s".formatted(task.getTitle(), project.getCode()),
                assignee == null ? Map.of() : Map.of("assignee", assignee.getEmail()));
        notifyAssignee(user, task, assignee);
        return toResponse(user, task, organizations.today(user.organizationId()), true);
    }

    @Transactional
    public TaskResponse update(CurrentUser user, UUID taskId, TaskRequest request) {
        Task task = loadReadable(user, taskId);
        Project project = task.getProject();
        if (!access.canManage(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER", "Only the project's managers can edit tasks.");
        }
        access.requireOpen(project);
        OptimisticLock.check(request.version(), task.getVersion(), "Task");
        User previousAssignee = task.getAssignee();
        User assignee = resolveAssignee(project, request.assigneeId());
        task.update(resolveMilestone(user, project, request.milestoneId()), request.title().trim(),
                blankToNull(request.description()), assignee, request.priority(),
                request.weight() != null ? request.weight() : task.getWeight(), request.startDate(), request.dueDate());
        tasks.flush();
        UUID previousId = previousAssignee != null ? previousAssignee.getId() : null;
        UUID newId = assignee != null ? assignee.getId() : null;
        if (!Objects.equals(previousId, newId)) {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("before", previousAssignee != null ? previousAssignee.getEmail() : null);
            changes.put("after", assignee != null ? assignee.getEmail() : null);
            auditService.record(user, AuditAction.TASK_ASSIGNED, "Task", task.getId(), project.getId(),
                    "Reassigned task '%s'".formatted(task.getTitle()), changes);
            notifyAssignee(user, task, assignee);
        }
        return toResponse(user, task, organizations.today(user.organizationId()), true);
    }

    /**
     * Assignees may move their own work forward; cancelling, reopening and restoring change the plan
     * and are reserved for project managers.
     */
    @Transactional
    public TaskResponse transition(CurrentUser user, UUID taskId, TaskTransitionRequest request) {
        Task task = loadReadable(user, taskId);
        Project project = task.getProject();
        access.requireOpen(project);
        OptimisticLock.check(request.version(), task.getVersion(), "Task");
        boolean manages = access.canManage(user, project);
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.userId());
        TaskStatus from = task.getStatus();
        TaskStatus target = request.target();

        if (!manages) {
            if (!isAssignee || !user.has(Permission.TASK_UPDATE_STATUS)) {
                throw new ForbiddenException("NOT_ASSIGNEE", "You can only update tasks assigned to you.");
            }
            if (TaskStatus.requiresManager(from, target)) {
                throw new ForbiddenException("NOT_PROJECT_MANAGER", "Only a project manager can make this change.");
            }
        }
        if ((target == TaskStatus.BLOCKED || target == TaskStatus.CANCELLED)
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException("REASON_REQUIRED", "reason", "Explain why the task is " + target + ".");
        }
        task.transitionTo(target, blankToNull(request.reason()), clock.instant());
        tasks.flush();
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("before", from);
        changes.put("after", target);
        auditService.record(user, AuditAction.TASK_STATUS_CHANGED, "Task", task.getId(), project.getId(),
                "Task '%s': %s → %s".formatted(task.getTitle(), from, target), changes);
        return toResponse(user, task, organizations.today(user.organizationId()), manages);
    }

    /** Weighted progress (blueprint 11.3): sum of DONE weights / sum of non-cancelled weights, rounded down. */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> progressByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return toPercentMap(tasks.progressByProject(projectIds));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> progressByMilestone(Collection<UUID> milestoneIds) {
        if (milestoneIds.isEmpty()) {
            return Map.of();
        }
        return toPercentMap(tasks.progressByMilestone(milestoneIds));
    }

    public static int progressPercent(long doneWeight, long totalWeight) {
        return totalWeight == 0 ? 0 : (int) (doneWeight * 100 / totalWeight);
    }

    @Transactional(readOnly = true)
    public long countOpen(UUID projectId) {
        return tasks.countByProjectIdAndStatusIn(projectId, TaskStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public long countOpenAssigned(UUID projectId, UUID userId) {
        return tasks.countByProjectIdAndAssigneeIdAndStatusIn(projectId, userId, TaskStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public long countOpenAssignedTo(CurrentUser user) {
        return tasks.count((root, query, cb) -> cb.and(
                cb.equal(root.get("assignee").get("id"), user.userId()),
                cb.equal(root.get("project").get("organizationId"), user.organizationId()),
                root.get("status").in(TaskStatus.OPEN)));
    }

    @Transactional(readOnly = true)
    public long countOpenInMilestone(UUID milestoneId) {
        return tasks.countByMilestoneIdAndStatusIn(milestoneId, TaskStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public long countOverdue(Collection<UUID> projectIds, LocalDate today) {
        return projectIds.isEmpty() ? 0 : tasks.countOverdue(projectIds, today);
    }

    @Transactional(readOnly = true)
    public Map<TaskStatus, Long> statusCounts(UUID projectId) {
        Map<TaskStatus, Long> counts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        tasks.countByStatus(projectId).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    private PageResponse<TaskResponse> toPage(CurrentUser user, Page<Task> page, LocalDate today) {
        Set<UUID> managed = user.isAdmin() ? Set.of() : new HashSet<>(access.managedProjectIds(user));
        return PageResponse.of(page, page.getContent().stream()
                .map(t -> toResponse(user, t, today, user.isAdmin()
                        || (user.role() == Role.PROJECT_MANAGER && managed.contains(t.getProject().getId()))))
                .toList());
    }

    private TaskResponse toResponse(CurrentUser user, Task t, LocalDate today, boolean manages) {
        boolean isAssignee = t.getAssignee() != null && t.getAssignee().getId().equals(user.userId());
        boolean projectOpen = !t.getProject().getStatus().isClosed();
        Set<TaskStatus> allowed = EnumSet.noneOf(TaskStatus.class);
        if (projectOpen && (manages || (isAssignee && user.has(Permission.TASK_UPDATE_STATUS)))) {
            allowed.addAll(t.getStatus().allowedTargets().stream()
                    .filter(target -> manages || !TaskStatus.requiresManager(t.getStatus(), target))
                    .collect(Collectors.toSet()));
        }
        Project p = t.getProject();
        return new com.constructflow.task.dto.TaskDtos.TaskResponse(t.getId(),
                new ProjectRef(p.getId(), p.getCode(), p.getName()),
                t.getMilestone() != null ? new MilestoneRef(t.getMilestone().getId(), t.getMilestone().getName()) : null,
                t.getTitle(), t.getDescription(), UserSummary.of(t.getAssignee()), t.getPriority(), t.getStatus(),
                t.getStatusReason(), t.getWeight(), t.getStartDate(), t.getDueDate(), t.getCompletedAt(),
                t.isOverdue(today), allowed, manages && projectOpen, t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private Task loadReadable(CurrentUser user, UUID taskId) {
        Task task = tasks.findInOrganization(taskId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Task"));
        access.requireReadable(user, task.getProject().getId());
        return task;
    }

    private User resolveAssignee(Project project, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!access.isUserOnProject(project.getId(), assigneeId)) {
            throw new BadRequestException("ASSIGNEE_NOT_MEMBER", "assigneeId", "The assignee must be a member of the project.");
        }
        return users.getReferenceById(assigneeId);
    }

    private Milestone resolveMilestone(CurrentUser user, Project project, UUID milestoneId) {
        if (milestoneId == null) {
            return null;
        }
        Milestone milestone = milestones.findInOrganization(milestoneId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Milestone"));
        if (!milestone.getProject().getId().equals(project.getId())) {
            throw new BadRequestException("MILESTONE_OTHER_PROJECT", "milestoneId", "The milestone belongs to another project.");
        }
        return milestone;
    }

    private void notifyAssignee(CurrentUser user, Task task, User assignee) {
        if (assignee != null && !assignee.getId().equals(user.userId())) {
            notifications.notify(user.organizationId(), assignee.getId(), NotificationType.TASK_ASSIGNED,
                    "New task: " + task.getTitle(),
                    task.getProject().getCode() + (task.getDueDate() != null ? " · due " + task.getDueDate() : ""),
                    "/projects/%s/tasks?task=%s".formatted(task.getProject().getId(), task.getId()), null);
        }
    }

    private static Map<UUID, Integer> toPercentMap(List<TaskRepository.ProgressRow> rows) {
        Map<UUID, Integer> result = new HashMap<>();
        rows.forEach(r -> result.put(r.getGroupId(),
                progressPercent(r.getDoneWeight().longValue(), r.getTotalWeight().longValue())));
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
