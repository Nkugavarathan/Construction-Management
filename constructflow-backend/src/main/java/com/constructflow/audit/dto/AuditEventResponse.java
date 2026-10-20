package com.constructflow.audit.dto;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.entity.AuditEvent;
import com.constructflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, AuditAction action, String entityType, UUID entityId, UUID projectId,
                                 String summary, String changes, UserSummary actor, String correlationId,
                                 Instant createdAt) {

    public static AuditEventResponse of(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getAction(), event.getEntityType(), event.getEntityId(),
                event.getProjectId(), event.getSummary(), event.getChanges(), UserSummary.of(event.getActor()),
                event.getCorrelationId(), event.getCreatedAt());
    }
}
