package com.constructflow.safety.dto;

import com.constructflow.safety.entity.SafetyIncident;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class SafetyDtos {

    private SafetyDtos() {
    }

    public record ReportIncidentRequest(
            @NotNull @PastOrPresent Instant occurredAt,
            @NotBlank @Size(max = 200) String location,
            @NotNull SafetyIncident.Severity severity,
            @NotBlank @Size(max = 4000) String description) {
    }

    public record UpdateIncidentRequest(
            @NotNull SafetyIncident.Severity severity,
            @Size(max = 4000) String correctiveActions,
            SafetyIncident.Status status,
            @NotNull Long version) {
    }

    public record IncidentResponse(UUID id, UUID projectId, Instant occurredAt, String location,
                                   SafetyIncident.Severity severity, String description, String correctiveActions,
                                   SafetyIncident.Status status, UserSummary reportedBy, UserSummary closedBy,
                                   Instant closedAt, Long version, Instant createdAt) {

        public static IncidentResponse of(SafetyIncident i) {
            return new IncidentResponse(i.getId(), i.getProject().getId(), i.getOccurredAt(), i.getLocation(),
                    i.getSeverity(), i.getDescription(), i.getCorrectiveActions(), i.getStatus(),
                    UserSummary.of(i.getReportedBy()), UserSummary.of(i.getClosedBy()), i.getClosedAt(), i.getVersion(),
                    i.getCreatedAt());
        }
    }
}
