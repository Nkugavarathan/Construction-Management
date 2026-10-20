package com.constructflow.organization.repository;

import com.constructflow.organization.entity.MembershipStatus;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    /** Runs on every authenticated request; fetches user and organization in the same query. */
    @Query("""
            select m from OrganizationMembership m
            join fetch m.user join fetch m.organization
            where m.user.id = :userId and m.organization.id = :organizationId
            """)
    Optional<OrganizationMembership> findMembership(@Param("userId") UUID userId,
                                                    @Param("organizationId") UUID organizationId);

    @Query("""
            select m from OrganizationMembership m join fetch m.organization
            where m.user.id = :userId and m.status = com.constructflow.organization.entity.MembershipStatus.ACTIVE
            order by m.createdAt
            """)
    List<OrganizationMembership> findActiveForUser(@Param("userId") UUID userId);

    @Query(value = """
            select m from OrganizationMembership m join fetch m.user u
            where m.organization.id = :organizationId
              and (:role is null or m.role = :role)
              and (:status is null or m.status = :status)
              and (:q is null or lower(u.fullName) like :q or u.email like :q)
            """,
            countQuery = """
            select count(m) from OrganizationMembership m join m.user u
            where m.organization.id = :organizationId
              and (:role is null or m.role = :role)
              and (:status is null or m.status = :status)
              and (:q is null or lower(u.fullName) like :q or u.email like :q)
            """)
    Page<OrganizationMembership> search(@Param("organizationId") UUID organizationId,
                                        @Param("q") String q,
                                        @Param("role") Role role,
                                        @Param("status") MembershipStatus status,
                                        Pageable pageable);

    @Query("select m from OrganizationMembership m join fetch m.user where m.id = :id and m.organization.id = :organizationId")
    Optional<OrganizationMembership> findByIdAndOrganizationId(@Param("id") UUID id,
                                                               @Param("organizationId") UUID organizationId);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, Role role, MembershipStatus status);

    boolean existsByOrganizationIdAndUserIdAndStatus(UUID organizationId, UUID userId, MembershipStatus status);

    List<OrganizationMembership> findByOrganizationIdAndRoleInAndStatus(UUID organizationId, List<Role> roles,
                                                                      MembershipStatus status);
}
