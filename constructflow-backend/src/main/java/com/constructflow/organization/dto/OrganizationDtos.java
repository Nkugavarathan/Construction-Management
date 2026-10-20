package com.constructflow.organization.dto;

import com.constructflow.organization.entity.MembershipStatus;
import com.constructflow.organization.entity.Organization;
import com.constructflow.organization.entity.OrganizationMembership;
import com.constructflow.security.Role;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record OrganizationResponse(UUID id, String name, String slug, String currencyCode, String timezone,
                                       BigDecimal expenseApprovalThreshold, Long version) {

        public static OrganizationResponse of(Organization o) {
            return new OrganizationResponse(o.getId(), o.getName(), o.getSlug(), o.getCurrencyCode(), o.getTimezone(),
                    o.getExpenseApprovalThreshold(), o.getVersion());
        }
    }

    public record UpdateOrganizationRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code") String currencyCode,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal expenseApprovalThreshold,
            @NotNull Long version) {
    }

    public record MemberResponse(UUID id, UserSummary user, Role role, MembershipStatus status, Instant joinedAt,
                                 Instant lastLoginAt, Long version) {

        public static MemberResponse of(OrganizationMembership m) {
            return new MemberResponse(m.getId(), UserSummary.of(m.getUser()), m.getRole(), m.getStatus(),
                    m.getCreatedAt(), m.getUser().getLastLoginAt(), m.getVersion());
        }
    }

    public record CreateMemberRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Role role,
            @NotBlank @Size(min = 12, max = 128, message = "must be between 12 and 128 characters") String temporaryPassword) {
    }

    public record UpdateMemberRequest(Role role, MembershipStatus status, @NotNull Long version) {
    }
}
