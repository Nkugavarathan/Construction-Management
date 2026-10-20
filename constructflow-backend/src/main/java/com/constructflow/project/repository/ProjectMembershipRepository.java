package com.constructflow.project.repository;

import com.constructflow.project.entity.ProjectMembership;
import com.constructflow.project.entity.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {

    Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserIdAndProjectRole(UUID projectId, UUID userId, ProjectRole projectRole);

    @Query("select m from ProjectMembership m join fetch m.user where m.project.id = :projectId order by m.projectRole, m.user.fullName")
    List<ProjectMembership> findByProjectWithUser(@Param("projectId") UUID projectId);

    @Query("select m.user.id from ProjectMembership m where m.project.id = :projectId and m.projectRole = :role")
    List<UUID> findUserIdsByRole(@Param("projectId") UUID projectId, @Param("role") ProjectRole role);

    @Query("select m.project.id from ProjectMembership m where m.user.id = :userId and m.organizationId = :organizationId and m.projectRole = :role")
    List<UUID> findProjectIdsByUserAndRole(@Param("userId") UUID userId, @Param("organizationId") UUID organizationId,
                                           @Param("role") ProjectRole role);
}
