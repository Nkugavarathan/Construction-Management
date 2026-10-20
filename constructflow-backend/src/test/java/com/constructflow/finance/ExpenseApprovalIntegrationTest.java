package com.constructflow.finance;

import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures.Org;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FIN-1..5: expense workflow, segregation of duties, approval limits, concurrency and metrics. */
class ExpenseApprovalIntegrationTest extends IntegrationTest {

    private Org org;
    private CurrentUser engineer;
    private CurrentUser finance;
    private CurrentUser pm;
    private ProjectResponse project;

    @BeforeEach
    void setUp() {
        org = fixtures.organization();
        engineer = org.user(Role.SITE_ENGINEER);
        finance = org.user(Role.FINANCE_OFFICER);
        pm = org.user(Role.PROJECT_MANAGER);
        project = fixtures.activeProject(org, new BigDecimal("1000000.00"));
    }

    @Test
    void submitApproveWritesHistoryAuditAndMetrics() throws Exception {
        String id = submitted(engineer, "250000.00");
        postAs(finance, "/api/v1/expenses/" + id + "/approve", Map.of("version", 1, "comment", "OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value("250000.00"));

        getAs(finance, "/api/v1/expenses/" + id + "/history")
                .andExpect(jsonPath("$[*].action").value(org.hamcrest.Matchers.contains("SUBMITTED", "APPROVED")));
        getAs(org.user(Role.ADMIN), "/api/v1/audit-events?entityId=" + id)
                .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.hasItems("EXPENSE_SUBMITTED", "EXPENSE_APPROVED")));
        getAs(pm, "/api/v1/projects/" + project.id() + "/financial-summary")
                .andExpect(jsonPath("$.approvedSpend").value("250000.00"))
                .andExpect(jsonPath("$.remainingBudget").value("750000.00"))
                .andExpect(jsonPath("$.utilizationPercent").value("25.00"));
    }

    @Test
    void approvedExpensesCannotBeEditedOnlyReversed() throws Exception {
        String id = submitted(engineer, "1000.00");
        postAs(finance, "/api/v1/expenses/" + id + "/approve", Map.of("version", 1)).andExpect(status().isOk());

        patchAs(engineer, "/api/v1/expenses/" + id, expenseBody("1.00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_EXPENSE_STATE"));

        String reversalId = read(postAs(finance, "/api/v1/expenses/" + id + "/reversal", Map.of("reason", "Duplicate invoice"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("-1000.00"))
                .andExpect(jsonPath("$.status").value("SUBMITTED")), "$.id");
        postAs(finance, "/api/v1/expenses/" + id + "/reversal", Map.of("reason", "Again"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REVERSED"));

        // The reversal is approved by someone other than its requester; the net approved spend returns to zero.
        postAs(org.user(Role.ADMIN), "/api/v1/expenses/" + reversalId + "/approve", Map.of("version", 0)).andExpect(status().isOk());
        getAs(finance, "/api/v1/projects/" + project.id() + "/financial-summary")
                .andExpect(jsonPath("$.approvedSpend").value("0.00"));
    }

    @Test
    void segregationOfDutiesAndApprovalLimits() throws Exception {
        String own = submitted(finance, "500.00");
        postAs(finance, "/api/v1/expenses/" + own + "/approve", Map.of("version", 1))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_APPROVAL"));

        String large = submitted(engineer, "150000.00");
        postAs(pm, "/api/v1/expenses/" + large + "/approve", Map.of("version", 1))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("APPROVAL_LIMIT_EXCEEDED"));

        String small = submitted(engineer, "5000.00");
        postAs(pm, "/api/v1/expenses/" + small + "/approve", Map.of("version", 1)).andExpect(status().isOk());

        postAs(engineer, "/api/v1/expenses/" + large + "/approve", Map.of("version", 1)).andExpect(status().isForbidden());
        postAs(finance, "/api/v1/expenses/" + large + "/reject", Map.of("version", 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
    }

    @Test
    void siteEngineerSeesOnlyOwnExpenses() throws Exception {
        submitted(engineer, "10.00");
        submitted(finance, "20.00");
        getAs(engineer, "/api/v1/projects/" + project.id() + "/expenses").andExpect(jsonPath("$.totalElements").value(1));
        getAs(engineer, "/api/v1/projects/" + project.id() + "/financial-summary").andExpect(status().isForbidden());
        getAs(finance, "/api/v1/projects/" + project.id() + "/expenses").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void validationRejectsBadMoney() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(expenseBody("10.005"));
        postAs(engineer, "/api/v1/projects/" + project.id() + "/expenses", body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        body.put("amount", "-5.00");
        postAs(engineer, "/api/v1/projects/" + project.id() + "/expenses", body).andExpect(status().isBadRequest());
        body.put("amount", "5.00");
        body.put("expenseDate", LocalDate.now().plusDays(2).toString());
        postAs(engineer, "/api/v1/projects/" + project.id() + "/expenses", body).andExpect(status().isBadRequest());
    }

    /** FIN-3 AC4: two approvers at the same moment. Exactly one wins; the other gets a 409. */
    @Test
    void concurrentDecisionsProduceExactlyOneOutcome() throws Exception {
        String id = submitted(engineer, "700.00");
        CurrentUser admin = org.user(Role.ADMIN);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = List.of(
                () -> { start.await(); return postAs(finance, "/api/v1/expenses/" + id + "/approve", Map.of("version", 1)).andReturn().getResponse().getStatus(); },
                () -> { start.await(); return postAs(admin, "/api/v1/expenses/" + id + "/reject", Map.of("version", 1, "comment", "No")).andReturn().getResponse().getStatus(); });
        List<Future<Integer>> futures = new ArrayList<>();
        calls.forEach(c -> futures.add(pool.submit(c)));
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        getAs(finance, "/api/v1/expenses/" + id + "/history").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void allocationsCannotExceedBudget() throws Exception {
        postAs(pm, "/api/v1/projects/" + project.id() + "/budget-allocations", Map.of()).andExpect(status().is4xxClientError());
        mvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/projects/" + project.id() + "/budget-allocations"), finance)
                        .contentType("application/json")
                        .content(toJson(Map.of("allocations", List.of(
                                Map.of("categoryId", fixtures.category(org, "Labour"), "amount", "600000.00"),
                                Map.of("categoryId", fixtures.category(org, "Materials"), "amount", "500000.00"))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_BUDGET"));
    }

    private String submitted(CurrentUser user, String amount) throws Exception {
        String id = read(postAs(user, "/api/v1/projects/" + project.id() + "/expenses", expenseBody(amount))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")), "$.id");
        postAs(user, "/api/v1/expenses/" + id + "/submit", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        return id;
    }

    private Map<String, Object> expenseBody(String amount) {
        return Map.of("categoryId", fixtures.category(org, "Materials"), "description", "Concrete",
                "amount", amount, "expenseDate", LocalDate.now().toString());
    }
}
