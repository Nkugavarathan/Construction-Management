package com.constructflow.task.entity;

import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.InvalidTransitionException;
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

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class Task extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @Column(nullable = false)
    private String title;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    private String statusReason;

    /** Relative importance (1-10) used by the weighted progress calculation. */
    @Column(nullable = false)
    private short weight;

    private LocalDate startDate;

    private LocalDate dueDate;

    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Version
    private Long version;

    protected Task() {
    }

    public Task(Project project, Milestone milestone, String title, String description, User assignee,
                TaskPriority priority, int weight, LocalDate startDate, LocalDate dueDate, User createdBy) {
        this.project = project;
        this.createdBy = createdBy;
        this.status = TaskStatus.TODO;
        update(milestone, title, description, assignee, priority, weight, startDate, dueDate);
    }

    public final void update(Milestone milestone, String title, String description, User assignee,
                             TaskPriority priority, int weight, LocalDate startDate, LocalDate dueDate) {
        if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
            throw new BadRequestException("INVALID_DATES", "dueDate", "Due date must be on or after the start date.");
        }
        this.milestone = milestone;
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.priority = priority;
        this.weight = (short) weight;
        this.startDate = startDate;
        this.dueDate = dueDate;
    }

    public void transitionTo(TaskStatus target, String reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException("Task", status, target, status.allowedTargets());
        }
        this.status = target;
        this.statusReason = reason;
        // completed_at is set exactly when the task is DONE (also enforced by a CHECK constraint).
        this.completedAt = target == TaskStatus.DONE ? now : null;
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today) && status.isOpen();
    }

    public Project getProject() {
        return project;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getAssignee() {
        return assignee;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public int getWeight() {
        return weight;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Long getVersion() {
        return version;
    }
}
