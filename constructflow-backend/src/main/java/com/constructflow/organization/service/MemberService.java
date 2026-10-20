package com.constructflow.organization.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.organization.dto.OrganizationDtos.CreateMemberRequest;
import com.constructflow.organization.dto.OrganizationDtos.MemberResponse;
import com.constructflow.organization.dto.OrganizationDtos.UpdateMemberRequest;
import com.constructflow.organization.entity.MembershipStatus;
import com.constructflow.organization.entity.Organization;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.organization.repository.OrganizationMembershipRepository;
import com.constructflow.organization.repository.OrganizationRepository;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Team management. Onboarding without email (MVP): the admin sets a temporary password and the user
 * must change it at first login. An email that already has an account is simply added to this
 * organization; their existing password is never touched.
 */
@Service
public class MemberService {

    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public MemberService(OrganizationMembershipRepository memberships, OrganizationRepository organizations,
                         UserRepository users, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.memberships = memberships;
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> list(CurrentUser user, String q, Role role, MembershipStatus status,
                                             Pageable pageable) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(memberships.search(user.organizationId(), pattern, role, status, pageable),
                MemberResponse::of);
    }

    @Transactional
    public MemberResponse create(CurrentUser admin, CreateMemberRequest request) {
        Organization organization = organizations.findById(admin.organizationId())
                .orElseThrow(() -> new NotFoundException("Organization"));
        String email = User.normalizeEmail(request.email());
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            user = users.save(new User(email, passwordEncoder.encode(request.temporaryPassword()), request.fullName(), true));
        } else if (memberships.findMembership(user.getId(), organization.getId()).isPresent()) {
            throw new ConflictException("ALREADY_MEMBER", "This person is already a member of the organization.");
        }
        OrganizationMembership membership = memberships.save(new OrganizationMembership(organization, user, request.role()));
        auditService.record(admin, AuditAction.MEMBER_ADDED, "OrganizationMembership", membership.getId(), null,
                "Added %s as %s".formatted(user.getEmail(), request.role()), Map.of("role", request.role()));
        return MemberResponse.of(membership);
    }

    @Transactional
    public MemberResponse update(CurrentUser admin, UUID membershipId, UpdateMemberRequest request) {
        // Lock the organization row first so concurrent admin changes are serialized (last-admin rule).
        organizations.findByIdForUpdate(admin.organizationId()).orElseThrow(() -> new NotFoundException("Organization"));
        OrganizationMembership membership = memberships.findByIdAndOrganizationId(membershipId, admin.organizationId())
                .orElseThrow(() -> new NotFoundException("Member"));
        OptimisticLock.check(request.version(), membership.getVersion(), "Member");

        Role newRole = request.role() != null ? request.role() : membership.getRole();
        MembershipStatus newStatus = request.status() != null ? request.status() : membership.getStatus();
        boolean losesActiveAdmin = membership.getRole() == Role.ADMIN && membership.isActive()
                && (newRole != Role.ADMIN || newStatus != MembershipStatus.ACTIVE);
        if (losesActiveAdmin
                && memberships.countByOrganizationIdAndRoleAndStatus(admin.organizationId(), Role.ADMIN, MembershipStatus.ACTIVE) <= 1) {
            throw new ConflictException("LAST_ADMIN", "An organization must keep at least one active administrator.");
        }

        String email = membership.getUser().getEmail();
        if (newRole != membership.getRole()) {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("before", membership.getRole());
            changes.put("after", newRole);
            auditService.record(admin, AuditAction.MEMBER_ROLE_CHANGED, "OrganizationMembership", membership.getId(),
                    null, "Changed role of %s from %s to %s".formatted(email, membership.getRole(), newRole), changes);
            membership.changeRole(newRole);
        }
        if (newStatus != membership.getStatus()) {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("before", membership.getStatus());
            changes.put("after", newStatus);
            auditService.record(admin, AuditAction.MEMBER_STATUS_CHANGED, "OrganizationMembership", membership.getId(),
                    null, "%s %s".formatted(newStatus == MembershipStatus.DISABLED ? "Disabled" : "Re-enabled", email), changes);
            membership.changeStatus(newStatus);
        }
        memberships.flush();
        return MemberResponse.of(membership);
    }

    /** Recipients for role-targeted notifications (e.g. all finance officers). */
    @Transactional(readOnly = true)
    public List<UUID> activeUserIdsWithRoles(UUID organizationId, List<Role> roles) {
        return memberships.findByOrganizationIdAndRoleInAndStatus(organizationId, roles, MembershipStatus.ACTIVE)
                .stream().map(m -> m.getUser().getId()).toList();
    }

    /** Used by other modules (e.g. project membership) to check that a user is an active member. */
    @Transactional(readOnly = true)
    public OrganizationMembership requireActiveMember(UUID organizationId, UUID userId) {
        return memberships.findMembership(userId, organizationId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> new NotFoundException("Organization member"));
    }
}
