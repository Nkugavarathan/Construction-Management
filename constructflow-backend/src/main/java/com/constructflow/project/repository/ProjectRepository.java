package com.constructflow.project.repository;

import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    /** Tenant-scoped lookup: a project from another organization behaves exactly like a missing one. */
    @EntityGraph(attributePaths = "manager")
    Optional<Project> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCase(UUID organizationId, String code);

    /** Loads the manager in the same query to avoid one extra SELECT per row (N+1). */
    @Override
    @EntityGraph(attributePaths = "manager")
    Page<Project> findAll(Specification<Project> spec, Pageable pageable);

    @Query("select p.id from Project p where p.organizationId = :organizationId")
    List<UUID> findIdsByOrganization(@Param("organizationId") UUID organizationId);

    @Query("""
            select p.id from Project p where p.organizationId = :organizationId
              and exists (select 1 from ProjectMembership m where m.project = p and m.user.id = :userId)
            """)
    List<UUID> findIdsVisibleToMember(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    @Query("select p.status as status, count(p) as total from Project p where p.id in :ids group by p.status")
    List<StatusCount> countByStatus(@Param("ids") List<UUID> ids);

    @EntityGraph(attributePaths = "manager")
    List<Project> findByIdInAndStatus(List<UUID> ids, ProjectStatus status);

    interface StatusCount {
        ProjectStatus getStatus();

        long getTotal();
    }
}
