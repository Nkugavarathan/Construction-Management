package com.constructflow.project.entity;

import com.constructflow.common.exception.InvalidTransitionException;
import com.constructflow.common.persistence.TimestampedEntity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project extends TimestampedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String clientName;

    private String clientContact;

    @Column(nullable = false)
    private String siteAddress;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate plannedEndDate;

    private LocalDate actualEndDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal approvedBudget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    private String statusReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Version
    private Long version;

    protected Project() {
    }

    public Project(UUID organizationId, String code, String name, String description, String clientName,
                   String clientContact, String siteAddress, LocalDate startDate, LocalDate plannedEndDate,
                   BigDecimal approvedBudget, User manager) {
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.clientName = clientName;
        this.clientContact = clientContact;
        this.siteAddress = siteAddress;
        this.startDate = startDate;
        this.plannedEndDate = plannedEndDate;
        this.approvedBudget = approvedBudget;
        this.manager = manager;
        this.status = ProjectStatus.PLANNING;
    }

    /** Applies a lifecycle transition, or throws if the state machine does not allow it. */
    public void transitionTo(ProjectStatus target, String reason, LocalDate today) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException("Project", status, target, status.allowedTargets());
        }
        this.status = target;
        this.statusReason = reason;
        if (target == ProjectStatus.COMPLETED) {
            this.actualEndDate = today;
        }
    }

    public void updateDetails(String name, String description, String clientName, String clientContact,
                              String siteAddress, LocalDate startDate, LocalDate plannedEndDate,
                              BigDecimal approvedBudget) {
        this.name = name;
        this.description = description;
        this.clientName = clientName;
        this.clientContact = clientContact;
        this.siteAddress = siteAddress;
        this.startDate = startDate;
        this.plannedEndDate = plannedEndDate;
        this.approvedBudget = approvedBudget;
    }

    public void changeManager(User manager) {
        this.manager = manager;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientContact() {
        return clientContact;
    }

    public String getSiteAddress() {
        return siteAddress;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getPlannedEndDate() {
        return plannedEndDate;
    }

    public LocalDate getActualEndDate() {
        return actualEndDate;
    }

    public BigDecimal getApprovedBudget() {
        return approvedBudget;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public User getManager() {
        return manager;
    }

    public Long getVersion() {
        return version;
    }
}
