package com.constructflow.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal for one request: who the user is, which organization they are acting
 * in and their role there. Built from the database on every request (not from the token alone), so a
 * role change or a disabled membership takes effect immediately.
 *
 * <p>Services always take the organization ID from here, never from request input.
 */
public record CurrentUser(UUID userId, UUID organizationId, Role role, String email, String fullName,
                          boolean mustChangePassword) {

    public Set<Permission> permissions() {
        return mustChangePassword ? Set.of() : RolePermissions.of(role);
    }

    public boolean has(Permission permission) {
        return permissions().contains(permission);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean seesAllProjects() {
        return RolePermissions.hasOrganizationWideProjectVisibility(role);
    }
}
