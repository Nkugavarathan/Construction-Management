package com.constructflow.project;

import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures.Org;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRJ-1..4, TSK-1..5: project lifecycle, task assignment and status rules over HTTP. */
class ProjectWorkflowIntegrationTest extends IntegrationTest {

    private Org org;
    private CurrentUser pm;
    private CurrentUser engineer;
    private ProjectResponse project;

    @BeforeEach
    void setUp() {
        org = fixtures.organization();
        pm = org.user(Role.PROJECT_MANAGER);
        engineer = org.user(Role.SITE_ENGINEER);
        project = fixtures.activeProject(org, new BigDecimal("5000000.00"));
    }

    @Test
    void createValidatesDatesAndUniqueCode() throws Exception {
        Map<String, Object> body = projectBody("dup-" + System.nanoTime() % 100000);
        postAs(pm, "/api/v1/projects", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.myProjectRole").value("MANAGER"));
        postAs(pm, "/api/v1/projects", body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PROJECT_CODE"));

        Map<String, Object> badDates = projectBody("bad-" + System.nanoTime() % 100000);
        badDates.put("plannedEndDate", LocalDate.now().minusDays(1).toString());
        postAs(pm, "/api/v1/projects", badDates).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("plannedEndDate"));
    }

    @Test
    void rolesWithoutCreatePermissionAreRejected() throws Exception {
        postAs(org.user(Role.VIEWER), "/api/v1/projects", projectBody("V-1")).andExpect(status().isForbidden());
        postAs(engineer, "/api/v1/projects", projectBody("E-1")).andExpect(status().isForbidden());
    }

    @Test
    void lifecycleIsEnforcedAndCompletionNeedsNoOpenWork() throws Exception {
        String url = "/api/v1/projects/" + project.id() + "/transitions";
        postAs(pm, url, Map.of("target", "PLANNING", "version", project.version()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        postAs(pm, url, Map.of("target", "ON_HOLD", "version", project.version()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        String taskId = read(postAs(pm, "/api/v1/projects/" + project.id() + "/tasks",
                Map.of("title", "Open task", "priority", "LOW")), "$.id");
        postAs(pm, url, Map.of("target", "COMPLETED", "version", project.version()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_HAS_OPEN_WORK"));

        postAs(pm, "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "DONE", "version", 0)).andExpect(status().isOk());
        postAs(pm, url, Map.of("target", "COMPLETED", "version", project.version()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progressPercent").value(100));

        // Completed projects are read-only.
        postAs(pm, "/api/v1/projects/" + project.id() + "/tasks", Map.of("title", "Late", "priority", "LOW"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_CLOSED"));
    }

    @Test
    void assigneeMovesOwnTaskButOnlyManagerCancels() throws Exception {
        String taskId = read(postAs(pm, "/api/v1/projects/" + project.id() + "/tasks",
                Map.of("title", "Column formwork", "priority", "HIGH", "assigneeId", engineer.userId(), "weight", 3))
                .andExpect(status().isCreated()), "$.id");

        postAs(engineer, "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "IN_PROGRESS", "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(engineer, "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "CANCELLED", "reason", "x", "version", 1))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_PROJECT_MANAGER"));
        postAs(engineer, "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "BLOCKED", "version", 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        // A stale version is a conflict, not a silent overwrite.
        postAs(engineer, "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "DONE", "version", 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        postAs(engineer, "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "DONE", "version", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").isString());

        getAs(engineer, "/api/v1/projects/" + project.id()).andExpect(jsonPath("$.progressPercent").value(100));
    }

    @Test
    void viewerCannotUpdateTasksAndAssigneeMustBeOnProject() throws Exception {
        String taskId = read(postAs(pm, "/api/v1/projects/" + project.id() + "/tasks",
                Map.of("title", "Survey", "priority", "LOW")), "$.id");
        postAs(org.user(Role.VIEWER), "/api/v1/tasks/" + taskId + "/transitions", Map.of("target", "IN_PROGRESS", "version", 0))
                .andExpect(status().isForbidden());
        postAs(pm, "/api/v1/projects/" + project.id() + "/tasks",
                Map.of("title", "x", "priority", "LOW", "assigneeId", org.user(Role.FINANCE_OFFICER).userId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_MEMBER"));
    }

    @Test
    void overdueFilterUsesDueDateAndOpenStatus() throws Exception {
        postAs(pm, "/api/v1/projects/" + project.id() + "/tasks", Map.of("title", "Late one", "priority", "LOW",
                "dueDate", LocalDate.now().minusDays(3).toString())).andExpect(jsonPath("$.overdue").value(true));
        postAs(pm, "/api/v1/projects/" + project.id() + "/tasks", Map.of("title", "Future", "priority", "LOW",
                "dueDate", LocalDate.now().plusDays(3).toString())).andExpect(jsonPath("$.overdue").value(false));
        getAs(pm, "/api/v1/projects/" + project.id() + "/tasks?overdue=true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Late one"));
    }

    @Test
    void siteReportsAreOnePerAuthorPerDay() throws Exception {
        Map<String, Object> report = Map.of("reportDate", LocalDate.now().toString(), "workforceCount", 12,
                "workPerformed", "Slab curing");
        postAs(engineer, "/api/v1/projects/" + project.id() + "/site-reports", report).andExpect(status().isCreated());
        postAs(engineer, "/api/v1/projects/" + project.id() + "/site-reports", report)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SITE_REPORT"));
    }

    @Test
    void removingMemberWithOpenTasksIsBlocked() throws Exception {
        postAs(pm, "/api/v1/projects/" + project.id() + "/tasks",
                Map.of("title", "Assigned", "priority", "LOW", "assigneeId", engineer.userId()));
        mvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/projects/" + project.id() + "/members/" + engineer.userId()), pm))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_HAS_OPEN_TASKS"));
    }

    private static Map<String, Object> projectBody(String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Library block");
        body.put("clientName", "City Council");
        body.put("siteAddress", "Main St");
        body.put("startDate", LocalDate.now().toString());
        body.put("plannedEndDate", LocalDate.now().plusDays(100).toString());
        body.put("approvedBudget", "2500000.00");
        return body;
    }
}
