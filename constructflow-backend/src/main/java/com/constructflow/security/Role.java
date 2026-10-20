package com.constructflow.security;

/** Organization-scoped roles. A user can hold a different role in each organization. */
public enum Role {
    ADMIN,
    PROJECT_MANAGER,
    SITE_ENGINEER,
    FINANCE_OFFICER,
    STOREKEEPER,
    VIEWER
}
