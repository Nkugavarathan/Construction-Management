package com.constructflow.safety.repository;

import com.constructflow.safety.entity.SafetyIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SafetyIncidentRepository extends JpaRepository<SafetyIncident, UUID> {

    @EntityGraph(attributePaths = {"project", "reportedBy", "closedBy"})
    @Query("select i from SafetyIncident i where i.id = :id and i.project.organizationId = :organizationId")
    Optional<SafetyIncident> findInOrganization(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = {"reportedBy", "closedBy"})
    @Query("""
            select i from SafetyIncident i where i.project.id = :projectId
              and (:status is null or i.status = :status)
              and (:severity is null or i.severity = :severity)
            """)
    Page<SafetyIncident> search(@Param("projectId") UUID projectId,
                                @Param("status") SafetyIncident.Status status,
                                @Param("severity") SafetyIncident.Severity severity, Pageable pageable);

    @Query("select count(i) from SafetyIncident i where i.project.id in :projectIds and i.status <> com.constructflow.safety.entity.SafetyIncident.Status.CLOSED")
    long countOpen(@Param("projectIds") Collection<UUID> projectIds);
}
