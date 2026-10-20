package com.constructflow.project.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.project.dto.ProjectDtos.AddProjectMemberRequest;
import com.constructflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.constructflow.project.dto.ProjectDtos.ProjectMemberResponse;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.project.dto.ProjectDtos.ProjectSummaryResponse;
import com.constructflow.project.dto.ProjectDtos.TransitionProjectRequest;
import com.constructflow.project.dto.ProjectDtos.UpdateProjectRequest;
import com.constructflow.project.entity.ProjectStatus;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.project.service.ProjectService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Thin controller: declares the HTTP contract and the coarse permission, validates the body with
 * {@code @Valid}, and delegates everything else to the service layer.
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService memberService;

    public ProjectController(ProjectService projectService, ProjectMemberService memberService) {
        this.projectService = projectService;
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public PageResponse<ProjectSummaryResponse> list(@AuthenticationPrincipal CurrentUser user,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) ProjectStatus status,
                                                     @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return projectService.search(user, q, status, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROJECT_CREATE')")
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse created = projectService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ProjectResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        return projectService.get(user, projectId);
    }

    @PatchMapping("/{projectId}")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    public ProjectResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(user, projectId, request);
    }

    @PostMapping("/{projectId}/transitions")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    @Operation(summary = "Move the project through its lifecycle (PLANNING, ACTIVE, ON_HOLD, COMPLETED, CANCELLED)")
    public ProjectResponse transition(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                      @Valid @RequestBody TransitionProjectRequest request) {
        return projectService.transition(user, projectId, request);
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public List<ProjectMemberResponse> members(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
        return memberService.list(user, projectId);
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasAuthority('PROJECT_MEMBER_MANAGE')")
    public ResponseEntity<ProjectMemberResponse> addMember(@AuthenticationPrincipal CurrentUser user,
                                                           @PathVariable UUID projectId,
                                                           @Valid @RequestBody AddProjectMemberRequest request) {
        return ResponseEntity.status(201).body(memberService.add(user, projectId, request));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasAuthority('PROJECT_MEMBER_MANAGE')")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                             @PathVariable UUID userId) {
        memberService.remove(user, projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
