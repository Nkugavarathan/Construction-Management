package com.constructflow.task.entity;

import com.constructflow.common.persistence.TimestampedEntity;
import com.constructflow.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "milestones")
public class Milestone extends TimestampedEntity {

    public enum Status { PENDING, COMPLETED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private Instant completedAt;

    @Version
    private Long version;

    protected Milestone() {
    }

    public Milestone(Project project, String name, String description, LocalDate dueDate) {
        this.project = project;
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.status = Status.PENDING;
    }

    public void update(String name, String description, LocalDate dueDate) {
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
    }

    public void complete(Instant now) {
        this.status = Status.COMPLETED;
        this.completedAt = now;
    }

    public void reopen() {
        this.status = Status.PENDING;
        this.completedAt = null;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getVersion() {
        return version;
    }
}
