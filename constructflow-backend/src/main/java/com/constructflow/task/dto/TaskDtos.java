package com.constructflow.task.dto;

import com.constructflow.task.entity.Milestone;
import com.constructflow.task.entity.TaskPriority;
import com.constructflow.task.entity.TaskStatus;
import com.constructflow.user.dto.UserSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record ProjectRef(UUID id, String code, String name) {
    }

    public record MilestoneRef(UUID id, String name) {
    }

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            UUID assigneeId,
            UUID milestoneId,
            @NotNull TaskPriority priority,
            @Min(1) @Max(10) Integer weight,
            LocalDate startDate,
            LocalDate dueDate,
            Long version) {
    }

    public record TaskTransitionRequest(@NotNull TaskStatus target, @Size(max = 500) String reason,
                                        @NotNull Long version) {
    }

    public record TaskResponse(UUID id, ProjectRef project, MilestoneRef milestone, String title, String description,
                               UserSummary assignee, TaskPriority priority, TaskStatus status, String statusReason,
                               int weight, LocalDate startDate, LocalDate dueDate, Instant completedAt,
                               boolean overdue, Set<TaskStatus> allowedTransitions, boolean canEdit, Long version,
                               Instant createdAt, Instant updatedAt) {
    }

    public record MilestoneRequest(@NotBlank @Size(max = 160) String name, @Size(max = 2000) String description,
                                   @NotNull LocalDate dueDate, Milestone.Status status, Long version) {
    }

    public record MilestoneResponse(UUID id, UUID projectId, String name, String description, LocalDate dueDate,
                                    Milestone.Status status, Instant completedAt, int progressPercent,
                                    boolean overdue, Long version) {
    }

    public record SiteReportRequest(@NotNull @PastOrPresent LocalDate reportDate, @Size(max = 80) String weather,
                                    @NotNull @Min(0) @Max(10000) Integer workforceCount,
                                    @NotBlank @Size(max = 4000) String workPerformed,
                                    @Size(max = 4000) String issues) {
    }

    public record SiteReportResponse(UUID id, UUID projectId, UserSummary author, LocalDate reportDate, String weather,
                                     int workforceCount, String workPerformed, String issues, Instant createdAt) {
    }
}
