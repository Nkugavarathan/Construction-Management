package com.constructflow.risk.dto;

import com.constructflow.risk.entity.Risk;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RiskDtos {

    private RiskDtos() {
    }

    /** The score is never accepted from the client; it is always computed as likelihood x impact. */
    public record RiskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull @Min(1) @Max(5) Integer likelihood,
            @NotNull @Min(1) @Max(5) Integer impact,
            @Size(max = 4000) String mitigation,
            UUID ownerId,
            LocalDate dueDate,
            Risk.Status status,
            Long version) {
    }

    public record RiskResponse(UUID id, UUID projectId, String title, String description, int likelihood, int impact,
                               int score, Risk.Level level, String mitigation, UserSummary owner, LocalDate dueDate,
                               Risk.Status status, Long version, Instant updatedAt) {

        public static RiskResponse of(Risk r) {
            return new RiskResponse(r.getId(), r.getProject().getId(), r.getTitle(), r.getDescription(), r.getLikelihood(),
                    r.getImpact(), r.getScore(), r.getLevel(), r.getMitigation(), UserSummary.of(r.getOwner()),
                    r.getDueDate(), r.getStatus(), r.getVersion(), r.getUpdatedAt());
        }
    }
}
