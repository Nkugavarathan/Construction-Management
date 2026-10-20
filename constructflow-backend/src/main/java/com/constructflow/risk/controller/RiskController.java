package com.constructflow.risk.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.risk.dto.RiskDtos.RiskRequest;
import com.constructflow.risk.dto.RiskDtos.RiskResponse;
import com.constructflow.risk.entity.Risk;
import com.constructflow.risk.service.RiskService;
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
@Tag(name = "Risks")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/projects/{projectId}/risks")
    @PreAuthorize("hasAuthority('RISK_READ')")
    public PageResponse<RiskResponse> risks(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                            @RequestParam(required = false) Risk.Status status,
                                            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        return riskService.list(user, projectId, status, pageable);
    }

    @PostMapping("/projects/{projectId}/risks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RISK_MANAGE')")
    public RiskResponse createRisk(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                   @Valid @RequestBody RiskRequest request) {
        return riskService.create(user, projectId, request);
    }

    @PatchMapping("/risks/{riskId}")
    @PreAuthorize("hasAuthority('RISK_MANAGE')")
    public RiskResponse updateRisk(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID riskId,
                                   @Valid @RequestBody RiskRequest request) {
        return riskService.update(user, riskId, request);
    }
}
