package com.constructflow.inventory;

import com.constructflow.inventory.service.InventoryService;
import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures.Org;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** INV-1..4: stock ledger, negative-stock prevention and concurrent issues (ADR-0004). */
class InventoryIntegrationTest extends IntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    private Org org;
    private CurrentUser store;
    private ProjectResponse project;
    private String materialId;

    @BeforeEach
    void setUp() throws Exception {
        org = fixtures.organization();
        store = org.user(Role.STOREKEEPER);
        project = fixtures.activeProject(org, new BigDecimal("100000.00"));
        materialId = read(postAs(store, "/api/v1/materials", Map.of("code", "CEM-" + System.nanoTime() % 100000,
                "name", "Cement", "unit", "BAG", "reorderLevel", "5")).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void ledgerRecordsEveryMovementWithRunningBalance() throws Exception {
        receive("100");
        postAs(store, "/api/v1/inventory/issues", Map.of("materialId", materialId, "projectId", project.id(), "quantity", "30.5"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanceAfter").value("69.500"));
        postAs(store, "/api/v1/inventory/adjustments", Map.of("materialId", materialId, "quantityDelta", "-4.5", "reason", "Damaged bags"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanceAfter").value("65.000"));

        getAs(store, "/api/v1/materials/" + materialId).andExpect(jsonPath("$.quantityOnHand").value("65.000"));
        getAs(store, "/api/v1/materials/" + materialId + "/movements")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"));
        assertThat(inventoryService.ledgerBalance(UUID.fromString(materialId))).isEqualByComparingTo("65");
    }

    @Test
    void issuingMoreThanStockIsRejectedAndChangesNothing() throws Exception {
        receive("10");
        postAs(store, "/api/v1/inventory/issues", Map.of("materialId", materialId, "projectId", project.id(), "quantity", "10.001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.available").value("10.000"));
        getAs(store, "/api/v1/materials/" + materialId).andExpect(jsonPath("$.quantityOnHand").value("10.000"));
        getAs(store, "/api/v1/materials/" + materialId + "/movements").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adjustmentNeedsReasonAndRolePermission() throws Exception {
        postAs(store, "/api/v1/inventory/adjustments", Map.of("materialId", materialId, "quantityDelta", "5"))
                .andExpect(status().isBadRequest());
        postAs(org.user(Role.SITE_ENGINEER), "/api/v1/inventory/adjustments",
                Map.of("materialId", materialId, "quantityDelta", "5", "reason", "x")).andExpect(status().isForbidden());
        postAs(org.user(Role.PROJECT_MANAGER), "/api/v1/inventory/issues",
                Map.of("materialId", materialId, "projectId", project.id(), "quantity", "1")).andExpect(status().isForbidden());
    }

    /**
     * INV-3: stock 10, ten storekeepers each issue 2 at the same instant. The row lock serializes them:
     * exactly 5 succeed, 5 get 409, the balance ends at 0 and the ledger agrees.
     */
    @Test
    void concurrentIssuesNeverOversell() throws Exception {
        receive("10");
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return postAs(store, "/api/v1/inventory/issues",
                        Map.of("materialId", materialId, "projectId", project.id(), "quantity", "2"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 201) {
                created++;
            } else if (code == 409) {
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(5);
        assertThat(conflicts).isEqualTo(5);
        getAs(store, "/api/v1/materials/" + materialId).andExpect(jsonPath("$.quantityOnHand").value("0.000"));
        assertThat(inventoryService.ledgerBalance(UUID.fromString(materialId))).isEqualByComparingTo("0");
    }

    @Test
    void issuesOnlyToActiveProjects() throws Exception {
        receive("5");
        postAs(org.user(Role.PROJECT_MANAGER), "/api/v1/projects/" + project.id() + "/transitions",
                Map.of("target", "ON_HOLD", "reason", "Permit", "version", project.version())).andExpect(status().isOk());
        postAs(store, "/api/v1/inventory/issues", Map.of("materialId", materialId, "projectId", project.id(), "quantity", "1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_NOT_ACTIVE"));
    }

    private void receive(String quantity) throws Exception {
        postAs(store, "/api/v1/inventory/receipts", Map.of("materialId", materialId, "quantity", quantity, "reason", "Delivery"))
                .andExpect(status().isCreated());
    }
}
