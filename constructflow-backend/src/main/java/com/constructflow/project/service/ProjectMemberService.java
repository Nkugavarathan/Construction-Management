package com.constructflow.project.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.organization.service.MemberService;
import com.constructflow.project.dto.ProjectDtos.AddProjectMemberRequest;
import com.constructflow.project.dto.ProjectDtos.ProjectMemberResponse;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectMembership;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.repository.ProjectMembershipRepository;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectMemberService {

    private final ProjectMembershipRepository memberships;
    private final ProjectAccessPolicy access;
    private final MemberService organizationMembers;
    private final TaskService tasks;
    private final AuditService auditService;

    public ProjectMemberService(ProjectMembershipRepository memberships, ProjectAccessPolicy access,
                                MemberService organizationMembers, TaskService tasks, AuditService auditService) {
        this.memberships = memberships;
        this.access = access;
        this.organizationMembers = organizationMembers;
        this.tasks = tasks;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(CurrentUser user, UUID projectId) {
        Project project = access.requireReadable(user, projectId);
        return memberships.findByProjectWithUser(project.getId()).stream()
                .map(m -> new ProjectMemberResponse(m.getUser().getId(), m.getUser().getFullName(), m.getUser().getEmail(),
                        m.getProjectRole(), organizationRole(user, m), m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ProjectMemberResponse add(CurrentUser user, UUID projectId, AddProjectMemberRequest request) {
        Project project = access.requireManageable(user, projectId);
        access.requireOpen(project);
        OrganizationMembership orgMember = organizationMembers.requireActiveMember(user.organizationId(), request.userId());
        if (request.projectRole() == ProjectRole.MANAGER
                && orgMember.getRole() != Role.PROJECT_MANAGER && orgMember.getRole() != Role.ADMIN) {
            throw new ConflictException("INVALID_PROJECT_ROLE",
                    "Only project managers and admins can be project managers.");
        }
        if (memberships.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new ConflictException("ALREADY_PROJECT_MEMBER", "This person is already on the project.");
        }
        ProjectMembership membership = memberships.save(new ProjectMembership(project, orgMember.getUser(), request.projectRole()));
        auditService.record(user, AuditAction.PROJECT_MEMBER_ADDED, "Project", project.getId(), project.getId(),
                "Added %s to %s as %s".formatted(orgMember.getUser().getEmail(), project.getCode(), request.projectRole()),
                Map.of("userId", request.userId(), "projectRole", request.projectRole()));
        return new ProjectMemberResponse(orgMember.getUser().getId(), orgMember.getUser().getFullName(),
                orgMember.getUser().getEmail(), membership.getProjectRole(), orgMember.getRole(), membership.getCreatedAt());
    }

    @Transactional
    public void remove(CurrentUser user, UUID projectId, UUID userId) {
        Project project = access.requireManageable(user, projectId);
        ProjectMembership membership = memberships.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new com.constructflow.common.exception.NotFoundException("Project member"));
        if (project.getManager().getId().equals(userId)) {
            throw new ConflictException("IS_PROJECT_MANAGER",
                    "Assign a different project manager before removing this person.");
        }
        long openTasks = tasks.countOpenAssigned(projectId, userId);
        if (openTasks > 0) {
            throw new ConflictException("MEMBER_HAS_OPEN_TASKS",
                    "Reassign this person's %d open task(s) first.".formatted(openTasks));
        }
        memberships.delete(membership);
        auditService.record(user, AuditAction.PROJECT_MEMBER_REMOVED, "Project", project.getId(), project.getId(),
                "Removed %s from %s".formatted(membership.getUser().getEmail(), project.getCode()),
                Map.of("userId", userId));
    }

    @Transactional(readOnly = true)
    public List<UUID> userIdsWithRole(UUID projectId, ProjectRole role) {
        return memberships.findUserIdsByRole(projectId, role);
    }

    private Role organizationRole(CurrentUser user, ProjectMembership m) {
        try {
            return organizationMembers.requireActiveMember(user.organizationId(), m.getUser().getId()).getRole();
        } catch (RuntimeException notActive) {
            return null;
        }
    }
}
