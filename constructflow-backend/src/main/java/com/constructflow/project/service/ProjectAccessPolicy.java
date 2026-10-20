package com.constructflow.project.service;

import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.repository.ProjectMembershipRepository;
import com.constructflow.project.repository.ProjectRepository;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Permission;
import com.constructflow.security.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Resource-level authorization for anything that lives inside a project. Controllers have already
 * checked the coarse permission ({@code @PreAuthorize}); this class answers "may <em>this</em> user act
 * on <em>this</em> project?".
 *
 * <ul>
 *   <li>Project in another organization: 404, identical to a project that does not exist.</li>
 *   <li>Same organization but not visible to the user: 403.</li>
 *   <li>Managing a project (tasks, milestones, members, risks): ADMIN, or a member with project role MANAGER.</li>
 * </ul>
 */
@Component
public class ProjectAccessPolicy {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;

    public ProjectAccessPolicy(ProjectRepository projects, ProjectMembershipRepository memberships) {
        this.projects = projects;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public Project requireReadable(CurrentUser user, UUID projectId) {
        Project project = load(user, projectId);
        if (!canRead(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MEMBER", "You are not a member of this project.");
        }
        return project;
    }

    @Transactional(readOnly = true)
    public Project requireManageable(CurrentUser user, UUID projectId) {
        Project project = requireReadable(user, projectId);
        if (!canManage(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MANAGER", "Only the project's managers can do this.");
        }
        return project;
    }

    public boolean canRead(CurrentUser user, Project project) {
        return user.seesAllProjects() || isMember(user, project);
    }

    public boolean canManage(CurrentUser user, Project project) {
        return user.isAdmin()
                || (user.role() == Role.PROJECT_MANAGER && isProjectManager(user, project));
    }

    public boolean isMember(CurrentUser user, Project project) {
        return memberships.existsByProjectIdAndUserId(project.getId(), user.userId());
    }

    public boolean isProjectManager(CurrentUser user, Project project) {
        return memberships.existsByProjectIdAndUserIdAndProjectRole(project.getId(), user.userId(), ProjectRole.MANAGER);
    }

    /**
     * Financial details of a project: admins and finance officers everywhere, project managers on
     * projects they manage. Site engineers only ever see their own expenses.
     */
    public boolean canViewFinance(CurrentUser user, Project project) {
        if (!user.has(Permission.FINANCE_READ)) {
            return false;
        }
        return user.isAdmin() || user.role() == Role.FINANCE_OFFICER
                || (user.role() == Role.PROJECT_MANAGER && isProjectManager(user, project));
    }

    /** Closed (completed or cancelled) projects are read-only. */
    public void requireOpen(Project project) {
        if (project.getStatus().isClosed()) {
            throw new ConflictException("PROJECT_CLOSED",
                    "Project %s is %s and can no longer be changed.".formatted(project.getCode(), project.getStatus()));
        }
    }

    /** IDs of every project the user can see, used by dashboards and cross-project lists. */
    @Transactional(readOnly = true)
    public List<UUID> visibleProjectIds(CurrentUser user) {
        return user.seesAllProjects()
                ? projects.findIdsByOrganization(user.organizationId())
                : projects.findIdsVisibleToMember(user.organizationId(), user.userId());
    }

    @Transactional(readOnly = true)
    public List<UUID> managedProjectIds(CurrentUser user) {
        return memberships.findProjectIdsByUserAndRole(user.userId(), user.organizationId(), ProjectRole.MANAGER);
    }

    private Project load(CurrentUser user, UUID projectId) {
        return projects.findByIdAndOrganizationId(projectId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Project"));
    }
}
