package com.constructflow.task.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.project.entity.Project;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/** Daily site diary entry. Immutable once submitted: it is a record of what happened on site that day. */
@Entity
@Immutable
@Table(name = "site_reports")
public class SiteReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private LocalDate reportDate;

    private String weather;

    @Column(nullable = false)
    private int workforceCount;

    @Column(nullable = false)
    private String workPerformed;

    private String issues;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SiteReport() {
    }

    public SiteReport(Project project, User author, LocalDate reportDate, String weather, int workforceCount,
                      String workPerformed, String issues) {
        this.project = project;
        this.author = author;
        this.reportDate = reportDate;
        this.weather = weather;
        this.workforceCount = workforceCount;
        this.workPerformed = workPerformed;
        this.issues = issues;
    }

    public Project getProject() {
        return project;
    }

    public User getAuthor() {
        return author;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getWeather() {
        return weather;
    }

    public int getWorkforceCount() {
        return workforceCount;
    }

    public String getWorkPerformed() {
        return workPerformed;
    }

    public String getIssues() {
        return issues;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
