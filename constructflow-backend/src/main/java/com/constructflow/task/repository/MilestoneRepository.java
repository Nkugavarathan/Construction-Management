package com.constructflow.task.repository;

import com.constructflow.task.entity.Milestone;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    @EntityGraph(attributePaths = "project")
    @Query("select m from Milestone m where m.id = :id and m.project.organizationId = :organizationId")
    Optional<Milestone> findInOrganization(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    List<Milestone> findByProjectIdOrderByDueDateAsc(UUID projectId);

    @EntityGraph(attributePaths = "project")
    @Query("""
            select m from Milestone m
            where m.project.id in :projectIds and m.status = com.constructflow.task.entity.Milestone.Status.PENDING
              and m.dueDate <= :until
            order by m.dueDate asc
            """)
    List<Milestone> findPendingDueBy(@Param("projectIds") Collection<UUID> projectIds, @Param("until") LocalDate until);
}
