package com.constructflow.auth.dto;

import com.constructflow.security.Permission;
import com.constructflow.security.Role;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response DTOs for the authentication API, grouped because they are small and only used together. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 160) String organizationName,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128, message = "must be between 12 and 128 characters") String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128, message = "must be between 12 and 128 characters") String newPassword) {
    }

    public record SwitchOrganizationRequest(@NotNull UUID organizationId) {
    }

    public record OrganizationSummary(UUID id, String name, String currencyCode, String timezone) {
    }

    public record MembershipSummary(UUID organizationId, String organizationName, Role role) {
    }

    /** Everything the frontend needs to render the shell: who I am, where I am, what I may do. */
    public record MeResponse(UserSummary user, OrganizationSummary activeOrganization, Role role,
                             Set<Permission> permissions, boolean mustChangePassword,
                             List<MembershipSummary> organizations) {
    }

    /** The refresh token is never in the body: it travels only in the HttpOnly cookie. */
    public record AuthResponse(String accessToken, Instant accessTokenExpiresAt, MeResponse me) {
    }
}
