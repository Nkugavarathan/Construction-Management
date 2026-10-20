package com.constructflow.security;

import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.organization.repository.OrganizationMembershipRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs after Spring Security has verified the JWT signature and expiry. It turns the token's
 * {@code sub} (user) and {@code org} (active organization) claims into a {@link CurrentUser} by
 * loading the membership from the database.
 *
 * <p>Throwing an {@code AuthenticationException} here makes the request fail with 401, so a member
 * who was disabled or removed a minute ago cannot keep using a still-unexpired token.
 */
@Component
public class MembershipAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OrganizationMembershipRepository memberships;

    public MembershipAuthenticationConverter(OrganizationMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        UUID organizationId;
        try {
            userId = UUID.fromString(jwt.getSubject());
            organizationId = UUID.fromString(jwt.getClaimAsString(JwtService.ORG_CLAIM));
        } catch (RuntimeException ex) {
            throw new InvalidBearerTokenException("Malformed token claims");
        }

        OrganizationMembership membership = memberships.findMembership(userId, organizationId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Membership is not active"));

        var user = membership.getUser();
        CurrentUser principal = new CurrentUser(user.getId(), organizationId, membership.getRole(),
                user.getEmail(), user.getFullName(), user.isMustChangePassword());
        return new CurrentUserAuthentication(principal, jwt);
    }
}
