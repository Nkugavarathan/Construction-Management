package com.constructflow.safety.entity;

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
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "safety_incidents")
public class SafetyIncident extends TimestampedEntity {

    public enum Severity { NEAR_MISS, MINOR, MAJOR, CRITICAL }

    /** OPEN -> INVESTIGATING -> CLOSED; a closed incident can be reopened for further investigation. */
    public enum Status {
        OPEN, INVESTIGATING, CLOSED;

        public Set<Status> allowedTargets() {
            return switch (this) {
                case OPEN -> EnumSet.of(INVESTIGATING, CLOSED);
                case INVESTIGATING -> EnumSet.of(CLOSED);
                case CLOSED -> EnumSet.of(INVESTIGATING);
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String description;

    private String correctiveActions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", updatable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    private Instant closedAt;

    @Version
    private Long version;

    protected SafetyIncident() {
    }

    public SafetyIncident(Project project, Instant occurredAt, String location, Severity severity, String description,
                          User reportedBy) {
        this.project = project;
        this.occurredAt = occurredAt;
        this.location = location;
        this.severity = severity;
        this.description = description;
        this.reportedBy = reportedBy;
        this.status = Status.OPEN;
    }

    public void updateInvestigation(Severity severity, String correctiveActions) {
        this.severity = severity;
        this.correctiveActions = correctiveActions;
    }

    /** Closing requires documented corrective actions. */
    public void transitionTo(Status target, User actor, Instant now) {
        if (!status.allowedTargets().contains(target)) {
            throw new InvalidTransitionException("Incident", status, target, status.allowedTargets());
        }
        if (target == Status.CLOSED && (correctiveActions == null || correctiveActions.isBlank())) {
            throw new BadRequestException("CORRECTIVE_ACTIONS_REQUIRED", "correctiveActions",
                    "Record the corrective actions before closing the incident.");
        }
        this.status = target;
        if (target == Status.CLOSED) {
            this.closedBy = actor;
            this.closedAt = now;
        } else {
            this.closedBy = null;
            this.closedAt = null;
        }
    }

    public Project getProject() {
        return project;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getLocation() {
        return location;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getCorrectiveActions() {
        return correctiveActions;
    }

    public Status getStatus() {
        return status;
    }

    public User getReportedBy() {
        return reportedBy;
    }

    public User getClosedBy() {
        return closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }
}
