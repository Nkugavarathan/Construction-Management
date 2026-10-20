package com.constructflow.audit.service;

import com.constructflow.audit.dto.AuditEventResponse;
import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.entity.AuditEvent;
import com.constructflow.audit.repository.AuditEventRepository;
import com.constructflow.common.response.PageResponse;
import com.constructflow.common.web.CorrelationIdFilter;
import com.constructflow.security.CurrentUser;
import com.constructflow.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes audit events <b>inside the caller's transaction</b> ({@code Propagation.MANDATORY}). If the
 * business change rolls back, its audit event rolls back too, so the log never claims something
 * happened that did not. Calling this outside a transaction is a programming error and fails fast.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final UserRepository users;
    private final JsonMapper jsonMapper;

    public AuditService(AuditEventRepository repository, UserRepository users, JsonMapper jsonMapper) {
        this.repository = repository;
        this.users = users;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(CurrentUser actor, AuditAction action, String entityType, UUID entityId, UUID projectId,
                       String summary, Map<String, ?> changes) {
        record(actor.organizationId(), actor.userId(), action, entityType, entityId, projectId, summary, changes);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID organizationId, UUID actorId, AuditAction action, String entityType, UUID entityId,
                       UUID projectId, String summary, Map<String, ?> changes) {
        String changesJson = changes == null || changes.isEmpty() ? null : jsonMapper.writeValueAsString(changes);
        AuditEvent event = new AuditEvent(organizationId, projectId,
                actorId == null ? null : users.getReferenceById(actorId),
                action, entityType, entityId, summary, changesJson, CorrelationIdFilter.current(), clientIp());
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(CurrentUser user, AuditAction action, String entityType,
                                                   UUID entityId, UUID projectId, UUID actorId,
                                                   Instant from, Instant to, Pageable pageable) {
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.parse("9999-12-31T00:00:00Z");
        String type = entityType == null || entityType.isBlank() ? null : entityType;
        return PageResponse.of(repository.search(user.organizationId(), action, type, entityId, projectId, actorId,
                effectiveFrom, effectiveTo, pageable), AuditEventResponse::of);
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
