package com.constructflow.auth.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.auth.dto.AuthDtos.ChangePasswordRequest;
import com.constructflow.auth.dto.AuthDtos.LoginRequest;
import com.constructflow.auth.dto.AuthDtos.MeResponse;
import com.constructflow.auth.dto.AuthDtos.MembershipSummary;
import com.constructflow.auth.dto.AuthDtos.OrganizationSummary;
import com.constructflow.auth.dto.AuthDtos.RegisterRequest;
import com.constructflow.auth.service.RefreshTokenService.IssuedRefreshToken;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.UnauthorizedException;
import com.constructflow.organization.entity.Organization;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.organization.repository.OrganizationMembershipRepository;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.JwtService;
import com.constructflow.security.JwtService.AccessToken;
import com.constructflow.security.LoginRateLimiter;
import com.constructflow.security.Role;
import com.constructflow.security.RolePermissions;
import com.constructflow.user.dto.UserSummary;
import com.constructflow.user.entity.User;
import com.constructflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationService organizationService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final LoginRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, OrganizationMembershipRepository memberships,
                       OrganizationService organizationService, RefreshTokenService refreshTokens,
                       LoginAttemptService loginAttempts, LoginRateLimiter rateLimiter, JwtService jwtService,
                       PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.organizationService = organizationService;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.rateLimiter = rateLimiter;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-only");
    }

    /** Creates organization + admin user + membership atomically, then starts a session. */
    @Transactional
    public AuthResult register(RegisterRequest request, ClientInfo client) {
        rateLimiter.check("register:" + client.ipAddress());
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists. Log in instead.");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName(), false));
        Organization organization = organizationService.createOrganization(request.organizationName());
        OrganizationMembership membership = memberships.save(new OrganizationMembership(organization, user, Role.ADMIN));
        auditService.record(organization.getId(), user.getId(), AuditAction.ORGANIZATION_CREATED, "Organization",
                organization.getId(), null, "Organization '%s' registered".formatted(organization.getName()), null);
        user.registerSuccessfulLogin(clock.instant());
        return startSession(user, membership, List.of(membership), client);
    }

    public AuthResult login(LoginRequest request, ClientInfo client) {
        rateLimiter.check("login:" + client.ipAddress());
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(clock.instant())) {
            throw new UnauthorizedException("ACCOUNT_LOCKED",
                    "Too many failed attempts. The account is locked for a few minutes.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttempts.recordFailure(user.getId());
            throw invalidCredentials();
        }
        List<OrganizationMembership> active = memberships.findActiveForUser(user.getId());
        if (active.isEmpty()) {
            throw new UnauthorizedException("NO_ACTIVE_MEMBERSHIP", "Your account is not active in any organization.");
        }
        loginAttempts.recordSuccess(user.getId());
        return startSession(user, active.get(0), active, client);
    }

    /** Rotates the refresh token and issues a new access token for the session's active organization. */
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("SESSION_EXPIRED", "Your session has expired. Please log in again.");
        }
        IssuedRefreshToken next = refreshTokens.rotate(rawRefreshToken, null, client);
        User user = next.token().getUser();
        OrganizationMembership membership = memberships
                .findMembership(user.getId(), next.token().getActiveOrganizationId())
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> {
                    refreshTokens.revokeFamily(next.rawValue());
                    return new UnauthorizedException("NO_ACTIVE_MEMBERSHIP",
                            "Your access to this organization has been removed.");
                });
        AccessToken accessToken = jwtService.issue(user.getId(), membership.getOrganization().getId());
        return new AuthResult(accessToken, next.rawValue(), buildMe(user, membership));
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revokeFamily(rawRefreshToken);
        }
    }

    public AuthResult switchOrganization(CurrentUser current, UUID organizationId, String rawRefreshToken,
                                         ClientInfo client) {
        OrganizationMembership target = memberships.findMembership(current.userId(), organizationId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> new NotFoundException("Organization"));
        if (rawRefreshToken == null) {
            throw new UnauthorizedException("SESSION_EXPIRED", "Your session has expired. Please log in again.");
        }
        IssuedRefreshToken next = refreshTokens.rotate(rawRefreshToken, organizationId, client);
        AccessToken accessToken = jwtService.issue(current.userId(), organizationId);
        return new AuthResult(accessToken, next.rawValue(), buildMe(target.getUser(), target));
    }

    /** Changing the password ends every other session (all refresh-token families are revoked). */
    @Transactional
    public AuthResult changePassword(CurrentUser current, ChangePasswordRequest request, ClientInfo client) {
        User user = users.findById(current.userId()).orElseThrow(() -> new NotFoundException("User"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("WRONG_PASSWORD", "currentPassword", "Current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BadRequestException("PASSWORD_REUSED", "newPassword", "Choose a password you have not just used.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAllForUser(user.getId());
        auditService.record(current, AuditAction.PASSWORD_CHANGED, "User", user.getId(), null,
                "Password changed", Map.of());
        OrganizationMembership membership = memberships.findMembership(user.getId(), current.organizationId())
                .orElseThrow(() -> new NotFoundException("Membership"));
        return startSession(user, membership, memberships.findActiveForUser(user.getId()), client);
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser current) {
        OrganizationMembership membership = memberships.findMembership(current.userId(), current.organizationId())
                .orElseThrow(() -> new NotFoundException("Membership"));
        return buildMe(membership.getUser(), membership);
    }

    private AuthResult startSession(User user, OrganizationMembership active, List<OrganizationMembership> all,
                                    ClientInfo client) {
        UUID organizationId = active.getOrganization().getId();
        IssuedRefreshToken refresh = refreshTokens.issueNewFamily(user, organizationId, client);
        AccessToken accessToken = jwtService.issue(user.getId(), organizationId);
        return new AuthResult(accessToken, refresh.rawValue(), buildMe(user, active, all));
    }

    private MeResponse buildMe(User user, OrganizationMembership active) {
        return buildMe(user, active, memberships.findActiveForUser(user.getId()));
    }

    private MeResponse buildMe(User user, OrganizationMembership active, List<OrganizationMembership> all) {
        Organization organization = active.getOrganization();
        Set<com.constructflow.security.Permission> permissions = user.isMustChangePassword()
                ? Set.of() : RolePermissions.of(active.getRole());
        List<MembershipSummary> organizations = all.stream()
                .map(m -> new MembershipSummary(m.getOrganization().getId(), m.getOrganization().getName(), m.getRole()))
                .toList();
        return new MeResponse(UserSummary.of(user),
                new OrganizationSummary(organization.getId(), organization.getName(), organization.getCurrencyCode(),
                        organization.getTimezone()),
                active.getRole(), permissions, user.isMustChangePassword(), organizations);
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    public record AuthResult(AccessToken accessToken, String refreshToken, MeResponse me) {
    }
}
