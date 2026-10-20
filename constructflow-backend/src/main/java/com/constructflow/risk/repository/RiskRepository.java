package com.constructflow.risk.repository;

import com.constructflow.risk.entity.Risk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskRepository extends JpaRepository<Risk, UUID> {

    @EntityGraph(attributePaths = {"project", "owner"})
    @Query("select r from Risk r where r.id = :id and r.project.organizationId = :organizationId")
    Optional<Risk> findInOrganization(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = "owner")
    @Query("""
            select r from Risk r where r.project.id = :projectId
              and (:status is null or r.status = :status)
            """)
    Page<Risk> search(@Param("projectId") UUID projectId, @Param("status") Risk.Status status, Pageable pageable);

    /** Scores of open risks in the given projects; the dashboard buckets them into levels. */
    @Query("select r.score from Risk r where r.project.id in :projectIds and r.status <> com.constructflow.risk.entity.Risk.Status.CLOSED")
    List<Short> openScores(@Param("projectIds") Collection<UUID> projectIds);

    @EntityGraph(attributePaths = "owner")
    @Query("""
            select r from Risk r where r.project.id = :projectId and r.status <> com.constructflow.risk.entity.Risk.Status.CLOSED
            order by r.score desc
            """)
    List<Risk> openByScore(@Param("projectId") UUID projectId, Pageable pageable);
}
