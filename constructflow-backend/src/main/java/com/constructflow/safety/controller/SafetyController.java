package com.constructflow.safety.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.safety.dto.SafetyDtos.IncidentResponse;
import com.constructflow.safety.dto.SafetyDtos.ReportIncidentRequest;
import com.constructflow.safety.dto.SafetyDtos.UpdateIncidentRequest;
import com.constructflow.safety.entity.SafetyIncident;
import com.constructflow.safety.service.SafetyService;
import com.constructflow.security.CurrentUser;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Safety")
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @GetMapping("/projects/{projectId}/incidents")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    public PageResponse<IncidentResponse> incidents(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                                    @RequestParam(required = false) SafetyIncident.Status status,
                                                    @RequestParam(required = false) SafetyIncident.Severity severity,
                                                    @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return safetyService.list(user, projectId, status, severity, pageable);
    }

    @PostMapping("/projects/{projectId}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INCIDENT_REPORT')")
    public IncidentResponse reportIncident(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                           @Valid @RequestBody ReportIncidentRequest request) {
        return safetyService.report(user, projectId, request);
    }

    @PatchMapping("/incidents/{incidentId}")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    public IncidentResponse updateIncident(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID incidentId,
                                           @Valid @RequestBody UpdateIncidentRequest request) {
        return safetyService.update(user, incidentId, request);
    }
}
