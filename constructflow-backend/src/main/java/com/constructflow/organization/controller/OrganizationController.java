package com.constructflow.organization.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.organization.dto.OrganizationDtos.CreateMemberRequest;
import com.constructflow.organization.dto.OrganizationDtos.MemberResponse;
import com.constructflow.organization.dto.OrganizationDtos.OrganizationResponse;
import com.constructflow.organization.dto.OrganizationDtos.UpdateMemberRequest;
import com.constructflow.organization.dto.OrganizationDtos.UpdateOrganizationRequest;
import com.constructflow.organization.entity.MembershipStatus;
import com.constructflow.organization.service.MemberService;
import com.constructflow.organization.service.OrganizationService;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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
@Tag(name = "Organization & team")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final MemberService memberService;

    public OrganizationController(OrganizationService organizationService, MemberService memberService) {
        this.organizationService = organizationService;
        this.memberService = memberService;
    }

    @GetMapping("/organization")
    public OrganizationResponse get(@AuthenticationPrincipal CurrentUser user) {
        return organizationService.get(user);
    }

    @PatchMapping("/organization")
    @PreAuthorize("hasAuthority('ORG_SETTINGS_MANAGE')")
    public OrganizationResponse update(@AuthenticationPrincipal CurrentUser user,
                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.update(user, request);
    }

    @GetMapping("/members")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    public PageResponse<MemberResponse> members(@AuthenticationPrincipal CurrentUser user,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) Role role,
                                                @RequestParam(required = false) MembershipStatus status,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return memberService.list(user, q, role, status, pageable);
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public MemberResponse addMember(@AuthenticationPrincipal CurrentUser user,
                                    @Valid @RequestBody CreateMemberRequest request) {
        return memberService.create(user, request);
    }

    @PatchMapping("/members/{membershipId}")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public MemberResponse updateMember(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID membershipId,
                                       @Valid @RequestBody UpdateMemberRequest request) {
        return memberService.update(user, membershipId, request);
    }
}
