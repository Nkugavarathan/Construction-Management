package com.constructflow.security;

import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures.Org;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-organization isolation (insecure direct object references). Organization B's admin, who has
 * every permission in B, tries to read and change organization A's records by ID. Every attempt must
 * answer 404, exactly as if the record did not exist.
 */
class TenantIsolationIntegrationTest extends IntegrationTest {

    private Org a;
    private Org b;
    private ProjectResponse projectA;
    private String taskA;
    private String expenseA;
    private String materialA;

    @BeforeEach
    void setUp() throws Exception {
        a = fixtures.organization();
        b = fixtures.organization();
        projectA = fixtures.activeProject(a, new BigDecimal("1000000.00"));
        taskA = read(postAs(a.user(Role.PROJECT_MANAGER), "/api/v1/projects/" + projectA.id() + "/tasks",
                Map.of("title", "Secret task", "priority", "HIGH")).andExpect(status().isCreated()), "$.id");
        expenseA = read(postAs(a.user(Role.SITE_ENGINEER), "/api/v1/projects/" + projectA.id() + "/expenses",
                Map.of("categoryId", fixtures.category(a, "Labour"), "description", "Wages", "amount", "100.00",
                        "expenseDate", LocalDate.now().toString())).andExpect(status().isCreated()), "$.id");
        materialA = read(postAs(a.user(Role.STOREKEEPER), "/api/v1/materials",
                Map.of("code", "MAT-" + System.nanoTime() % 100000, "name", "Cement", "unit", "BAG", "reorderLevel", "1"))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void foreignAdminGets404ForEveryResource() throws Exception {
        CurrentUser intruder = b.user(Role.ADMIN);
        for (String url : List.of(
                "/api/v1/projects/" + projectA.id(),
                "/api/v1/projects/" + projectA.id() + "/tasks",
                "/api/v1/projects/" + projectA.id() + "/members",
                "/api/v1/projects/" + projectA.id() + "/financial-summary",
                "/api/v1/projects/" + projectA.id() + "/documents",
                "/api/v1/reports/projects/" + projectA.id(),
                "/api/v1/tasks/" + taskA,
                "/api/v1/expenses/" + expenseA,
                "/api/v1/materials/" + materialA,
                "/api/v1/materials/" + materialA + "/movements")) {
            getAs(intruder, url).andExpect(status().isNotFound());
        }
    }

    @Test
    void foreignAdminCannotChangeAnything() throws Exception {
        CurrentUser intruder = b.user(Role.ADMIN);
        postAs(intruder, "/api/v1/projects/" + projectA.id() + "/transitions",
                Map.of("target", "ON_HOLD", "reason", "x", "version", projectA.version())).andExpect(status().isNotFound());
        postAs(intruder, "/api/v1/projects/" + projectA.id() + "/tasks", Map.of("title", "x", "priority", "LOW"))
                .andExpect(status().isNotFound());
        postAs(intruder, "/api/v1/expenses/" + expenseA + "/approve", Map.of("version", 0)).andExpect(status().isNotFound());
        postAs(intruder, "/api/v1/inventory/issues", Map.of("materialId", materialA, "projectId", projectA.id(),
                "quantity", "1")).andExpect(status().isNotFound());
        postAs(intruder, "/api/v1/inventory/receipts", Map.of("materialId", materialA, "quantity", "5", "reason", "x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsAndDashboardsNeverIncludeOtherOrganizations() throws Exception {
        getAs(b.user(Role.ADMIN), "/api/v1/projects")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(projectA.id().toString()))));
        getAs(b.user(Role.ADMIN), "/api/v1/materials")
                .andExpect(jsonPath("$.content[*].id", not(hasItem(materialA))));
        getAs(b.user(Role.ADMIN), "/api/v1/dashboard/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectsByStatus.ACTIVE").value(0));
        getAs(b.user(Role.ADMIN), "/api/v1/audit-events?entityId=" + projectA.id())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sameOrganizationNonMembersAreForbiddenNotHidden() throws Exception {
        // A second project manager in organization A who is not on the project: same tenant, so 403 rather than 404.
        CurrentUser otherPm = fixtures.addMember(a, Role.PROJECT_MANAGER);
        getAs(otherPm, "/api/v1/projects/" + projectA.id()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PROJECT_MEMBER"));
        getAs(otherPm, "/api/v1/projects").andExpect(jsonPath("$.content[*].id", not(hasItem(projectA.id().toString()))));
    }
}
