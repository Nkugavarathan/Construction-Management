package com.constructflow.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Spring Security {@code Authentication} whose principal is {@link CurrentUser} and whose granted
 * authorities are the user's permissions. That lets controllers write
 * {@code @PreAuthorize("hasAuthority('PROJECT_CREATE')")} and {@code @AuthenticationPrincipal CurrentUser}.
 */
public class CurrentUserAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser principal;
    private final Jwt token;

    public CurrentUserAuthentication(CurrentUser principal, Jwt token) {
        super(principal.permissions().stream().map(p -> new SimpleGrantedAuthority(p.name())).toList());
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public CurrentUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
