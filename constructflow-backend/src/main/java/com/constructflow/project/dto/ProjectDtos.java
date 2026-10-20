package com.constructflow.project.dto;

import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.security.Role;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,32}", message = "must be 2-32 letters, digits or dashes") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 160) String clientName,
            @Size(max = 160) String clientContact,
            @NotBlank @Size(max = 300) String siteAddress,
            @NotNull LocalDate startDate,
            @NotNull LocalDate plannedEndDate,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal approvedBudget,
            UUID managerId) {
    }

    /** PATCH semantics: null fields are left unchanged. Status changes use the transitions endpoint. */
    public record UpdateProjectRequest(
            @Size(min = 1, max = 160) String name,
            @Size(max = 4000) String description,
            @Size(min = 1, max = 160) String clientName,
            @Size(max = 160) String clientContact,
            @Size(min = 1, max = 300) String siteAddress,
            LocalDate startDate,
            LocalDate plannedEndDate,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal approvedBudget,
            UUID managerId,
            @NotNull Long version) {
    }

    public record TransitionProjectRequest(@NotNull ProjectStatus target, @Size(max = 500) String reason,
                                           @NotNull Long version) {
    }

    public record ProjectSummaryResponse(UUID id, String code, String name, String clientName, ProjectStatus status,
                                         UserSummary manager, LocalDate startDate, LocalDate plannedEndDate,
                                         BigDecimal approvedBudget, BigDecimal approvedSpend,
                                         BigDecimal budgetUtilizationPercent, int progressPercent) {
    }

    public record ProjectResponse(UUID id, String code, String name, String description, String clientName,
                                  String clientContact, String siteAddress, LocalDate startDate,
                                  LocalDate plannedEndDate, LocalDate actualEndDate, BigDecimal approvedBudget,
                                  ProjectStatus status, String statusReason, Set<ProjectStatus> allowedTransitions,
                                  UserSummary manager, int progressPercent, ProjectRole myProjectRole,
                                  boolean canManage, boolean canViewFinance, Long version, Instant createdAt,
                                  Instant updatedAt) {
    }

    public record ProjectMemberResponse(UUID userId, String fullName, String email, ProjectRole projectRole,
                                        Role organizationRole, Instant addedAt) {
    }

    public record AddProjectMemberRequest(@NotNull UUID userId, @NotNull ProjectRole projectRole) {
    }
}
