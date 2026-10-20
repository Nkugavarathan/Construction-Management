package com.constructflow.document.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import com.constructflow.project.entity.Project;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document extends TimestampedEntity {

    public enum Category { DRAWING, CONTRACT, PERMIT, SPECIFICATION, REPORT, PHOTO, INVOICE, OTHER }

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private int currentVersionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    /** Optimistic lock: two simultaneous "new version" uploads cannot both become version N+1. */
    @Version
    private Long version;

    protected Document() {
    }

    public Document(Project project, String title, Category category, User createdBy) {
        this.organizationId = project.getOrganizationId();
        this.project = project;
        this.title = title;
        this.category = category;
        this.createdBy = createdBy;
        this.currentVersionNo = 1;
    }

    public int nextVersion() {
        currentVersionNo++;
        return currentVersionNo;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Project getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public Category getCategory() {
        return category;
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Long getVersion() {
        return version;
    }
}
