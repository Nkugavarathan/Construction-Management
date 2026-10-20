package com.constructflow.task.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.security.CurrentUser;
import com.constructflow.task.dto.TaskDtos.MilestoneRequest;
import com.constructflow.task.dto.TaskDtos.MilestoneResponse;
import com.constructflow.task.dto.TaskDtos.SiteReportRequest;
import com.constructflow.task.dto.TaskDtos.SiteReportResponse;
import com.constructflow.task.dto.TaskDtos.TaskRequest;
import com.constructflow.task.dto.TaskDtos.TaskResponse;
import com.constructflow.task.dto.TaskDtos.TaskTransitionRequest;
import com.constructflow.task.entity.TaskStatus;
import com.constructflow.task.service.MilestoneService;
import com.constructflow.task.service.SiteReportService;
import com.constructflow.task.service.TaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tasks, milestones & site reports")
public class TaskController {

    private final TaskService taskService;
    private final MilestoneService milestoneService;
    private final SiteReportService siteReportService;

    public TaskController(TaskService taskService, MilestoneService milestoneService, SiteReportService siteReportService) {
        this.taskService = taskService;
        this.milestoneService = milestoneService;
        this.siteReportService = siteReportService;
    }

    @GetMapping("/projects/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                           @RequestParam(required = false) TaskStatus status,
                                           @RequestParam(required = false) UUID assigneeId,
                                           @RequestParam(required = false) UUID milestoneId,
                                           @RequestParam(defaultValue = "false") boolean overdue,
                                           @RequestParam(required = false) String q,
                                           @PageableDefault(size = 50, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return taskService.search(user, projectId, status, assigneeId, milestoneId, overdue, q, pageable);
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    public TaskResponse create(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.create(user, projectId, request);
    }

    @GetMapping("/tasks/mine")
    public PageResponse<TaskResponse> mine(@AuthenticationPrincipal CurrentUser user,
                                           @RequestParam(defaultValue = "false") boolean includeClosed,
                                           @PageableDefault(size = 50, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return taskService.mine(user, includeClosed, pageable);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public TaskResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID taskId) {
        return taskService.get(user, taskId);
    }

    @PatchMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('TASK_MANAGE')")
    public TaskResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID taskId,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.update(user, taskId, request);
    }

    @PostMapping("/tasks/{taskId}/transitions")
    @PreAuthorize("hasAuthority('TASK_UPDATE_STATUS')")
    public TaskResponse transition(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID taskId,
                                   @Valid @RequestBody TaskTransitionRequest request) {
        return taskService.transition(user, taskId, request);
    }

    @GetMapping("/projects/{projectId}/milestones")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public List<MilestoneResponse> milestones(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        return milestoneService.list(user, projectId);
    }

    @PostMapping("/projects/{projectId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MILESTONE_MANAGE')")
    public MilestoneResponse createMilestone(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                             @Valid @RequestBody MilestoneRequest request) {
        return milestoneService.create(user, projectId, request);
    }

    @PatchMapping("/milestones/{milestoneId}")
    @PreAuthorize("hasAuthority('MILESTONE_MANAGE')")
    public MilestoneResponse updateMilestone(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID milestoneId,
                                             @Valid @RequestBody MilestoneRequest request) {
        return milestoneService.update(user, milestoneId, request);
    }

    @GetMapping("/projects/{projectId}/site-reports")
    @PreAuthorize("hasAuthority('SITE_REPORT_READ')")
    public PageResponse<SiteReportResponse> siteReports(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable UUID projectId,
                                                        @PageableDefault(size = 20, sort = "reportDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return siteReportService.list(user, projectId, pageable);
    }

    @PostMapping("/projects/{projectId}/site-reports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SITE_REPORT_CREATE')")
    public SiteReportResponse createSiteReport(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                               @Valid @RequestBody SiteReportRequest request) {
        return siteReportService.create(user, projectId, request);
    }
}
