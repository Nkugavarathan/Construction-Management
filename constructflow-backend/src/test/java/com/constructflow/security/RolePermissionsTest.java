package com.constructflow.security;

import org.junit.jupiter.api.Test;

import static com.constructflow.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Spot checks of the role-permission matrix (blueprint 9.2). A change here should be a deliberate decision. */
class RolePermissionsTest {

    @Test
    void adminHasEverything() {
        assertThat(RolePermissions.of(Role.ADMIN)).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void viewerCannotModifyAnything() {
        assertThat(RolePermissions.of(Role.VIEWER)).allSatisfy(p -> assertThat(p.name()).endsWith("_READ"));
    }

    @Test
    void creationApprovalAndReceivingAreSeparated() {
        assertThat(RolePermissions.of(Role.STOREKEEPER)).contains(PO_CREATE, PO_RECEIVE).doesNotContain(PO_APPROVE);
        assertThat(RolePermissions.of(Role.FINANCE_OFFICER)).contains(PO_APPROVE, EXPENSE_APPROVE).doesNotContain(PO_RECEIVE, PO_CREATE);
    }

    @Test
    void siteEngineerCannotApproveOrManage() {
        assertThat(RolePermissions.of(Role.SITE_ENGINEER))
                .contains(TASK_UPDATE_STATUS, EXPENSE_CREATE, SITE_REPORT_CREATE, INCIDENT_REPORT)
                .doesNotContain(EXPENSE_APPROVE, TASK_MANAGE, MEMBER_MANAGE, AUDIT_READ);
    }

    @Test
    void onlyAdminsSeeTheAuditLogAndManageMembers() {
        for (Role role : Role.values()) {
            boolean admin = role == Role.ADMIN;
            assertThat(RolePermissions.of(role).contains(AUDIT_READ)).isEqualTo(admin);
            assertThat(RolePermissions.of(role).contains(MEMBER_MANAGE)).isEqualTo(admin);
        }
    }

    @Test
    void forcedPasswordChangeRemovesAllPermissions() {
        CurrentUser user = new CurrentUser(null, null, Role.ADMIN, "a@b.c", "A", true);
        assertThat(user.permissions()).isEmpty();
    }
}
