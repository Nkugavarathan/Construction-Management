package com.constructflow.project.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.finance.service.BudgetService;
import com.constructflow.finance.service.ExpenseService;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.organization.service.MemberService;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.constructflow.project.dto.ProjectDtos.TransitionProjectRequest;
import com.constructflow.project.dto.ProjectDtos.UpdateProjectRequest;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectMembership;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.project.mapper.ProjectMapper;
import com.constructflow.project.repository.ProjectMembershipRepository;
import com.constructflow.project.repository.ProjectRepository;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.task.service.TaskService;
import com.constructflow.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository projectMemberships;
    private final ProjectAccessPolicy access;
    private final MemberService members;
    private final OrganizationService organizations;
    private final TaskService tasks;
    private final ExpenseService expenses;
    private final BudgetService budgets;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projects, ProjectMembershipRepository projectMemberships,
                          ProjectAccessPolicy access, MemberService members, OrganizationService organizations,
                          TaskService tasks, ExpenseService expenses, BudgetService budgets, AuditService auditService) {
        this.projects = projects;
        this.projectMemberships = projectMemberships;
        this.access = access;
        this.members = members;
        this.organizations = organizations;
        this.tasks = tasks;
        this.expenses = expenses;
        this.budgets = budgets;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectResponse create(CurrentUser user, CreateProjectRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (projects.existsByOrganizationIdAndCodeIgnoreCase(user.organizationId(), code)) {
            throw new ConflictException("DUPLICATE_PROJECT_CODE", "Project code %s is already in use.".formatted(code));
        }
        validateDates(request.startDate(), request.plannedEndDate());
        User manager = resolveManager(user, request.managerId() != null ? request.managerId() : user.userId());

        Project project = projects.save(new Project(user.organizationId(), code, request.name().trim(),
                blankToNull(request.description()), request.clientName().trim(), blankToNull(request.clientContact()),
                request.siteAddress().trim(), request.startDate(), request.plannedEndDate(), request.approvedBudget(),
                manager));
        projectMemberships.save(new ProjectMembership(project, manager, ProjectRole.MANAGER));
        if (!manager.getId().equals(user.userId()) && user.role() == Role.PROJECT_MANAGER) {
            // A project manager creating a project for a colleague still needs to see it.
            projectMemberships.save(new ProjectMembership(project, members.requireActiveMember(user.organizationId(),
                    user.userId()).getUser(), ProjectRole.MANAGER));
        }
        auditService.record(user, AuditAction.PROJECT_CREATED, "Project", project.getId(), project.getId(),
                "Created project %s %s".formatted(code, project.getName()),
                Map.of("approvedBudget", request.approvedBudget(), "manager", manager.getEmail()));
        projects.flush();
        return detail(user, project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> search(CurrentUser user, String q, ProjectStatus status,
                                                       Pageable pageable) {
        Specification<Project> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), user.organizationId()));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("clientName")), pattern)));
            }
            if (!user.seesAllProjects()) {
                Subquery<UUID> member = query.subquery(UUID.class);
                var m = member.from(ProjectMembership.class);
                member.select(m.get("id")).where(cb.equal(m.get("project"), root),
                        cb.equal(m.get("user").get("id"), user.userId()));
                predicates.add(cb.exists(member));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Project> page = projects.findAll(spec, pageable);
        List<UUID> ids = page.getContent().stream().map(Project::getId).toList();

        // Two grouped queries for the whole page instead of two queries per project.
        Map<UUID, Integer> progress = tasks.progressByProject(ids);
        Set<UUID> financeVisible = new HashSet<>(financeVisibleIds(user, ids));
        Map<UUID, BigDecimal> spend = expenses.approvedSpendByProject(financeVisible);

        return PageResponse.of(page, page.getContent().stream()
                .map(p -> ProjectMapper.toSummary(p, progress.getOrDefault(p.getId(), 0),
                        financeVisible.contains(p.getId()) ? spend.getOrDefault(p.getId(), BigDecimal.ZERO) : null))
                .toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(CurrentUser user, UUID projectId) {
        return detail(user, access.requireReadable(user, projectId));
    }

    @Transactional
    public ProjectResponse update(CurrentUser user, UUID projectId, UpdateProjectRequest request) {
        Project project = access.requireManageable(user, projectId);
        access.requireOpen(project);
        OptimisticLock.check(request.version(), project.getVersion(), "Project");

        LocalDate start = request.startDate() != null ? request.startDate() : project.getStartDate();
        LocalDate end = request.plannedEndDate() != null ? request.plannedEndDate() : project.getPlannedEndDate();
        validateDates(start, end);
        BigDecimal budget = request.approvedBudget() != null ? request.approvedBudget() : project.getApprovedBudget();
        BigDecimal allocated = budgets.totalAllocated(projectId);
        if (budget.compareTo(allocated) < 0) {
            throw new BadRequestException("BUDGET_BELOW_ALLOCATIONS", "approvedBudget",
                    "Budget cannot be lower than the amount already allocated to categories (%s).".formatted(allocated));
        }

        Map<String, Object> before = snapshot(project);
        project.updateDetails(
                request.name() != null ? request.name().trim() : project.getName(),
                request.description() != null ? blankToNull(request.description()) : project.getDescription(),
                request.clientName() != null ? request.clientName().trim() : project.getClientName(),
                request.clientContact() != null ? blankToNull(request.clientContact()) : project.getClientContact(),
                request.siteAddress() != null ? request.siteAddress().trim() : project.getSiteAddress(),
                start, end, budget);
        if (request.managerId() != null && !request.managerId().equals(project.getManager().getId())) {
            User manager = resolveManager(user, request.managerId());
            project.changeManager(manager);
            projectMemberships.findByProjectIdAndUserId(projectId, manager.getId()).ifPresentOrElse(
                    m -> m.changeRole(ProjectRole.MANAGER),
                    () -> projectMemberships.save(new ProjectMembership(project, manager, ProjectRole.MANAGER)));
        }
        projects.flush();
        auditService.record(user, AuditAction.PROJECT_UPDATED, "Project", project.getId(), project.getId(),
                "Updated project " + project.getCode(), Map.of("before", before, "after", snapshot(project)));
        return detail(user, project);
    }

    /**
     * Lifecycle change. Completing requires that no work is open and no expense is waiting for approval,
     * so a "completed" project never has loose ends.
     */
    @Transactional
    public ProjectResponse transition(CurrentUser user, UUID projectId, TransitionProjectRequest request) {
        Project project = access.requireManageable(user, projectId);
        OptimisticLock.check(request.version(), project.getVersion(), "Project");
        ProjectStatus target = request.target();
        if (target.requiresReason() && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException("REASON_REQUIRED", "reason", "A reason is required for " + target + ".");
        }
        if (target == ProjectStatus.COMPLETED) {
            long openTasks = tasks.countOpen(projectId);
            long pending = expenses.countPending(projectId);
            if (openTasks > 0 || pending > 0) {
                throw new ConflictException("PROJECT_HAS_OPEN_WORK",
                        "Cannot complete: %d open task(s) and %d expense(s) awaiting approval.".formatted(openTasks, pending));
            }
        }
        ProjectStatus from = project.getStatus();
        project.transitionTo(target, blankToNull(request.reason()), organizations.today(user.organizationId()));
        projects.flush();
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("before", from);
        changes.put("after", target);
        if (request.reason() != null) {
            changes.put("reason", request.reason());
        }
        auditService.record(user, AuditAction.PROJECT_STATUS_CHANGED, "Project", project.getId(), project.getId(),
                "%s: %s → %s".formatted(project.getCode(), from, target), changes);
        return detail(user, project);
    }

    @Transactional(readOnly = true)
    public Map<ProjectStatus, Long> statusCounts(List<UUID> projectIds) {
        Map<ProjectStatus, Long> counts = new LinkedHashMap<>();
        for (ProjectStatus status : ProjectStatus.values()) {
            counts.put(status, 0L);
        }
        if (!projectIds.isEmpty()) {
            projects.countByStatus(projectIds).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        }
        return counts;
    }

    /** Active projects among the given IDs with progress and (where visible) spend, for dashboards. */
    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> activeSummaries(CurrentUser user, List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        List<Project> active = projects.findByIdInAndStatus(projectIds, ProjectStatus.ACTIVE);
        List<UUID> ids = active.stream().map(Project::getId).toList();
        Map<UUID, Integer> progress = tasks.progressByProject(ids);
        Set<UUID> financeVisible = new HashSet<>(financeVisibleIds(user, ids));
        Map<UUID, BigDecimal> spend = expenses.approvedSpendByProject(financeVisible);
        return active.stream()
                .map(p -> ProjectMapper.toSummary(p, progress.getOrDefault(p.getId(), 0),
                        financeVisible.contains(p.getId()) ? spend.getOrDefault(p.getId(), BigDecimal.ZERO) : null))
                .toList();
    }

    private ProjectResponse detail(CurrentUser user, Project project) {
        int progress = tasks.progressByProject(List.of(project.getId())).getOrDefault(project.getId(), 0);
        ProjectRole myRole = projectMemberships.findByProjectIdAndUserId(project.getId(), user.userId())
                .map(ProjectMembership::getProjectRole).orElse(null);
        return ProjectMapper.toResponse(project, progress, myRole, access.canManage(user, project),
                access.canViewFinance(user, project));
    }

    /** Of the given projects, those whose finances this user may see. */
    public List<UUID> financeVisibleIds(CurrentUser user, List<UUID> pageIds) {
        if (!user.has(com.constructflow.security.Permission.FINANCE_READ)) {
            return List.of();
        }
        if (user.isAdmin() || user.role() == Role.FINANCE_OFFICER) {
            return pageIds;
        }
        if (user.role() == Role.PROJECT_MANAGER) {
            Set<UUID> managed = new HashSet<>(access.managedProjectIds(user));
            return pageIds.stream().filter(managed::contains).toList();
        }
        return List.of();
    }

    private User resolveManager(CurrentUser user, UUID managerId) {
        OrganizationMembership membership = members.requireActiveMember(user.organizationId(), managerId);
        if (membership.getRole() != Role.PROJECT_MANAGER && membership.getRole() != Role.ADMIN) {
            throw new BadRequestException("INVALID_MANAGER", "managerId",
                    "The project manager must have the Project Manager or Admin role.");
        }
        return membership.getUser();
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BadRequestException("INVALID_DATES", "plannedEndDate",
                    "Planned completion must be on or after the start date.");
        }
    }

    private static Map<String, Object> snapshot(Project p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", p.getName());
        map.put("clientName", p.getClientName());
        map.put("startDate", p.getStartDate().toString());
        map.put("plannedEndDate", p.getPlannedEndDate().toString());
        map.put("approvedBudget", p.getApprovedBudget());
        map.put("manager", p.getManager().getEmail());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
