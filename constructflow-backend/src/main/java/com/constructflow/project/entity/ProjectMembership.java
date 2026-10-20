package com.constructflow.project.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Who works on a project. Drives "Proj" scoped visibility for project managers, engineers and viewers. */
@Entity
@Table(name = "project_memberships")
public class ProjectMembership extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole projectRole;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectMembership() {
    }

    public ProjectMembership(Project project, User user, ProjectRole projectRole) {
        this.organizationId = project.getOrganizationId();
        this.project = project;
        this.user = user;
        this.projectRole = projectRole;
    }

    public void changeRole(ProjectRole projectRole) {
        this.projectRole = projectRole;
    }

    public Project getProject() {
        return project;
    }

    public User getUser() {
        return user;
    }

    public ProjectRole getProjectRole() {
        return projectRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
