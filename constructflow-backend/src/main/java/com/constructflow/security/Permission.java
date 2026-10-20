package com.constructflow.security;

/**
 * Fine-grained actions checked by the API. Controllers check permissions, never role names, so the
 * role-to-permission mapping can change in one place ({@link RolePermissions}).
 *
 * <p>A permission answers "may this user do this kind of thing at all?". Whether they may do it on a
 * <em>specific</em> project (membership, segregation of duties, approval limits) is decided in the
 * service layer by policy classes.
 */
public enum Permission {
    ORG_SETTINGS_MANAGE,
    MEMBER_MANAGE,
    MEMBER_READ,

    PROJECT_CREATE,
    PROJECT_READ,
    PROJECT_UPDATE,
    PROJECT_MEMBER_MANAGE,

    TASK_MANAGE,
    TASK_UPDATE_STATUS,
    MILESTONE_MANAGE,
    SITE_REPORT_CREATE,
    SITE_REPORT_READ,

    BUDGET_MANAGE,
    FINANCE_READ,
    EXPENSE_CREATE,
    EXPENSE_APPROVE,

    MATERIAL_MANAGE,
    INVENTORY_READ,
    INVENTORY_RECEIVE,
    INVENTORY_ISSUE,
    INVENTORY_ADJUST,

    SUPPLIER_MANAGE,
    PROCUREMENT_READ,
    PO_CREATE,
    PO_APPROVE,
    PO_RECEIVE,

    DOCUMENT_UPLOAD,
    DOCUMENT_READ,

    RISK_MANAGE,
    RISK_READ,
    INCIDENT_REPORT,
    INCIDENT_MANAGE,
    INCIDENT_READ,

    AUDIT_READ,
    DASHBOARD_READ,
    REPORT_READ
}
