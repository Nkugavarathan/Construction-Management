package com.constructflow.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.constructflow.security.Permission.*;

/**
 * The role-permission matrix from docs/00-blueprint.md section 9.2, in code. Scope (organization-wide
 * versus "only my projects") is applied separately by {@code ProjectAccessPolicy}.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);

    static {
        MATRIX.put(Role.ADMIN, EnumSet.allOf(Permission.class));

        MATRIX.put(Role.PROJECT_MANAGER, EnumSet.of(
                MEMBER_READ,
                PROJECT_CREATE, PROJECT_READ, PROJECT_UPDATE, PROJECT_MEMBER_MANAGE,
                TASK_MANAGE, TASK_UPDATE_STATUS, MILESTONE_MANAGE, SITE_REPORT_CREATE, SITE_REPORT_READ,
                BUDGET_MANAGE, FINANCE_READ, EXPENSE_CREATE, EXPENSE_APPROVE,
                INVENTORY_READ,
                PROCUREMENT_READ, PO_CREATE, PO_APPROVE,
                DOCUMENT_UPLOAD, DOCUMENT_READ,
                RISK_MANAGE, RISK_READ, INCIDENT_REPORT, INCIDENT_MANAGE, INCIDENT_READ,
                DASHBOARD_READ, REPORT_READ));

        MATRIX.put(Role.SITE_ENGINEER, EnumSet.of(
                MEMBER_READ,
                PROJECT_READ,
                TASK_UPDATE_STATUS, SITE_REPORT_CREATE, SITE_REPORT_READ,
                FINANCE_READ, EXPENSE_CREATE,
                INVENTORY_READ,
                DOCUMENT_UPLOAD, DOCUMENT_READ,
                RISK_READ, INCIDENT_REPORT, INCIDENT_READ,
                DASHBOARD_READ));

        MATRIX.put(Role.FINANCE_OFFICER, EnumSet.of(
                MEMBER_READ,
                PROJECT_READ,
                SITE_REPORT_READ,
                BUDGET_MANAGE, FINANCE_READ, EXPENSE_CREATE, EXPENSE_APPROVE,
                INVENTORY_READ,
                PROCUREMENT_READ, PO_APPROVE,
                DOCUMENT_READ,
                DASHBOARD_READ, REPORT_READ));

        MATRIX.put(Role.STOREKEEPER, EnumSet.of(
                MEMBER_READ,
                PROJECT_READ,
                MATERIAL_MANAGE, INVENTORY_READ, INVENTORY_RECEIVE, INVENTORY_ISSUE, INVENTORY_ADJUST,
                SUPPLIER_MANAGE, PROCUREMENT_READ, PO_CREATE, PO_RECEIVE,
                INCIDENT_REPORT, INCIDENT_READ,
                DASHBOARD_READ));

        MATRIX.put(Role.VIEWER, EnumSet.of(
                PROJECT_READ,
                SITE_REPORT_READ,
                DOCUMENT_READ,
                RISK_READ, INCIDENT_READ,
                DASHBOARD_READ));
    }

    private RolePermissions() {
    }

    public static Set<Permission> of(Role role) {
        return Collections.unmodifiableSet(MATRIX.get(role));
    }

    /**
     * Roles that see every project in the organization. Everyone else sees only projects where they
     * are a project member.
     */
    public static boolean hasOrganizationWideProjectVisibility(Role role) {
        return role == Role.ADMIN || role == Role.FINANCE_OFFICER || role == Role.STOREKEEPER;
    }
}
