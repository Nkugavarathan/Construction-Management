package com.constructflow.risk.entity;

import com.constructflow.common.exception.BadRequestException;
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

import java.time.LocalDate;

@Entity
@Table(name = "risks")
public class Risk extends TimestampedEntity {

    public enum Status { OPEN, MITIGATING, CLOSED }

    /** 5x5 matrix bands for score = likelihood x impact. */
    public enum Level {
        LOW, MEDIUM, HIGH, CRITICAL;

        public static Level of(int score) {
            if (score >= 17) {
                return CRITICAL;
            }
            if (score >= 10) {
                return HIGH;
            }
            if (score >= 5) {
                return MEDIUM;
            }
            return LOW;
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private short likelihood;

    @Column(nullable = false)
    private short impact;

    /** Always likelihood x impact; recomputed on every change and checked by a database constraint. */
    @Column(nullable = false)
    private short score;

    private String mitigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Version
    private Long version;

    protected Risk() {
    }

    public Risk(Project project, String title, User createdBy) {
        this.project = project;
        this.title = title;
        this.createdBy = createdBy;
        this.status = Status.OPEN;
    }

    public void update(String title, String description, int likelihood, int impact, String mitigation, User owner,
                       LocalDate dueDate, Status status) {
        this.title = title;
        this.description = description;
        this.likelihood = (short) likelihood;
        this.impact = (short) impact;
        this.score = (short) score(likelihood, impact);
        this.mitigation = mitigation;
        this.owner = owner;
        this.dueDate = dueDate;
        this.status = status;
    }

    public static int score(int likelihood, int impact) {
        if (likelihood < 1 || likelihood > 5 || impact < 1 || impact > 5) {
            throw new BadRequestException("INVALID_RISK_RATING", "likelihood", "Likelihood and impact must be between 1 and 5.");
        }
        return likelihood * impact;
    }

    public Level getLevel() {
        return Level.of(score);
    }

    public Project getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getLikelihood() {
        return likelihood;
    }

    public int getImpact() {
        return impact;
    }

    public int getScore() {
        return score;
    }

    public String getMitigation() {
        return mitigation;
    }

    public User getOwner() {
        return owner;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Long getVersion() {
        return version;
    }
}
