package com.constructflow.audit.entity;

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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of an important change. {@code @Immutable} stops Hibernate issuing UPDATEs, and
 * a database trigger rejects UPDATE/DELETE from any client (V5 migration).
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(nullable = false)
    private String entityType;

    private UUID entityId;

    @Column(nullable = false)
    private String summary;

    /** Safe before/after values only. Never passwords, tokens or secrets. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    private String correlationId;

    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID organizationId, UUID projectId, User actor, AuditAction action, String entityType,
                      UUID entityId, String summary, String changes, String correlationId, String ipAddress) {
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary.length() > 500 ? summary.substring(0, 500) : summary;
        this.changes = changes;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public User getActor() {
        return actor;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public String getChanges() {
        return changes;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
