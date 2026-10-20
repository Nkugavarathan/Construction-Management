package com.constructflow.organization.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.finance.service.BudgetCategoryService;
import com.constructflow.organization.dto.OrganizationDtos.OrganizationResponse;
import com.constructflow.organization.dto.OrganizationDtos.UpdateOrganizationRequest;
import com.constructflow.organization.entity.Organization;
import com.constructflow.organization.repository.OrganizationRepository;
import com.constructflow.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganizationService {

    private static final String DEFAULT_CURRENCY = "LKR";
    private static final String DEFAULT_TIMEZONE = "Asia/Colombo";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("100000.00");

    private final OrganizationRepository organizations;
    private final BudgetCategoryService budgetCategories;
    private final AuditService auditService;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizations, BudgetCategoryService budgetCategories,
                               AuditService auditService, Clock clock) {
        this.organizations = organizations;
        this.budgetCategories = budgetCategories;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Part of registration; must run inside the registration transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Organization createOrganization(String name) {
        Organization organization = organizations.save(new Organization(name, uniqueSlug(name), DEFAULT_CURRENCY,
                DEFAULT_TIMEZONE, DEFAULT_THRESHOLD));
        budgetCategories.createDefaults(organization);
        return organization;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(CurrentUser user) {
        return OrganizationResponse.of(load(user.organizationId()));
    }

    @Transactional(readOnly = true)
    public Organization load(UUID organizationId) {
        return organizations.findById(organizationId).orElseThrow(() -> new NotFoundException("Organization"));
    }

    @Transactional
    public OrganizationResponse update(CurrentUser user, UpdateOrganizationRequest request) {
        Organization organization = load(user.organizationId());
        OptimisticLock.check(request.version(), organization.getVersion(), "Organization");
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException ex) {
            throw new BadRequestException("INVALID_TIMEZONE", "timezone", "Unknown time zone.");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("before", Map.of("name", organization.getName(), "currencyCode", organization.getCurrencyCode(),
                "timezone", organization.getTimezone(), "expenseApprovalThreshold", organization.getExpenseApprovalThreshold()));
        organization.updateSettings(request.name(), request.currencyCode(), request.timezone(),
                request.expenseApprovalThreshold());
        changes.put("after", Map.of("name", request.name(), "currencyCode", request.currencyCode(),
                "timezone", request.timezone(), "expenseApprovalThreshold", request.expenseApprovalThreshold()));
        auditService.record(user, AuditAction.ORGANIZATION_SETTINGS_CHANGED, "Organization", organization.getId(),
                null, "Organization settings updated", changes);
        organizations.flush();
        return OrganizationResponse.of(organization);
    }

    /** "Today" in the organization's own time zone: due dates and overdue checks use the site's calendar, not UTC. */
    @Transactional(readOnly = true)
    public LocalDate today(UUID organizationId) {
        return LocalDate.now(clock.withZone(ZoneId.of(load(organizationId).getTimezone())));
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "org";
        }
        base = base.length() > 60 ? base.substring(0, 60) : base;
        String slug = base;
        while (organizations.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }
}
