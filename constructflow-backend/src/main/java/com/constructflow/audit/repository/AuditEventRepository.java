package com.constructflow.audit.repository;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately extends the bare {@link Repository} rather than {@code JpaRepository}: only
 * {@code save} (insert) and queries exist, so no code path can update or delete audit events.
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    @Query(value = """
            select e from AuditEvent e left join fetch e.actor
            where e.organizationId = :organizationId
              and (:action is null or e.action = :action)
              and (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
              and (:projectId is null or e.projectId = :projectId)
              and (:actorId is null or e.actor.id = :actorId)
              and e.createdAt >= :from and e.createdAt < :to
            """,
            countQuery = """
            select count(e) from AuditEvent e
            where e.organizationId = :organizationId
              and (:action is null or e.action = :action)
              and (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
              and (:projectId is null or e.projectId = :projectId)
              and (:actorId is null or e.actor.id = :actorId)
              and e.createdAt >= :from and e.createdAt < :to
            """)
    Page<AuditEvent> search(@Param("organizationId") UUID organizationId,
                            @Param("action") AuditAction action,
                            @Param("entityType") String entityType,
                            @Param("entityId") UUID entityId,
                            @Param("projectId") UUID projectId,
                            @Param("actorId") UUID actorId,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);

    List<AuditEvent> findByOrganizationIdAndEntityIdOrderByCreatedAtAsc(UUID organizationId, UUID entityId);
}
