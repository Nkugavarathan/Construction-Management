package com.constructflow.procurement;

import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.Role;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures.Org;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Purchase-order approval and duplicate-proof goods receiving (ADR-0006). */
class GoodsReceivingIntegrationTest extends IntegrationTest {

    private Org org;
    private CurrentUser store;
    private CurrentUser finance;
    private String materialId;
    private String poId;
    private String lineId;

    @BeforeEach
    void setUp() throws Exception {
        org = fixtures.organization();
        store = org.user(Role.STOREKEEPER);
        finance = org.user(Role.FINANCE_OFFICER);
        ProjectResponse project = fixtures.activeProject(org, new BigDecimal("1000000.00"));
        materialId = read(postAs(store, "/api/v1/materials", Map.of("code", "STL-" + System.nanoTime() % 100000,
                "name", "Steel", "unit", "TONNE", "reorderLevel", "1")), "$.id");
        String supplierId = read(postAs(store, "/api/v1/suppliers", Map.of("name", "Supplier " + UUID.randomUUID()))
                .andExpect(status().isCreated()), "$.id");
        ResultActions created = postAs(store, "/api/v1/purchase-orders", Map.of("supplierId", supplierId,
                "projectId", project.id(), "items", List.of(Map.of("materialId", materialId, "quantity", "10", "unitPrice", "1000.125"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value("10001.25"))
                .andExpect(jsonPath("$.actions.submit").value(true));
        poId = read(created, "$.id");
        lineId = read(created, "$.items[0].id");
    }

    @Test
    void creatorCannotApproveAndReceivingRequiresApproval() throws Exception {
        submit();
        receive("key-before-approval-1", "DN-1", "1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_RECEIVABLE"));
        // Storekeepers hold PO_CREATE and PO_RECEIVE, not PO_APPROVE.
        postAs(store, "/api/v1/purchase-orders/" + poId + "/approve", Map.of("version", 1)).andExpect(status().isForbidden());
        approve();
    }

    @Test
    void retryWithSameKeyReturnsOriginalReceiptWithoutDoubleBooking() throws Exception {
        submit();
        approve();
        String key = "retry-" + UUID.randomUUID();
        String receiptId = read(receive(key, "DN-100", "4").andExpect(status().isCreated()), "$.id");
        receive(key, "DN-100", "4").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(receiptId));

        getAs(store, "/api/v1/materials/" + materialId).andExpect(jsonPath("$.quantityOnHand").value("4.000"));
        getAs(store, "/api/v1/purchase-orders/" + poId)
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.items[0].remaining").value("6.000"));

        // Same key, different content: refused rather than silently ignored.
        receive(key, "DN-100", "5").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void sameDeliveryNoteCannotBeBookedTwiceEvenWithNewKey() throws Exception {
        submit();
        approve();
        receive("first-" + UUID.randomUUID(), "DN-777", "2").andExpect(status().isCreated());
        receive("second-" + UUID.randomUUID(), "dn-777", "2").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DELIVERY_NOTE"));
    }

    @Test
    void overReceiptIsRejectedAndFullReceiptCompletesTheOrder() throws Exception {
        submit();
        approve();
        receive("over-" + UUID.randomUUID(), "DN-1", "10.5").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVER_RECEIPT"));
        receive("full-" + UUID.randomUUID(), "DN-2", "10").andExpect(status().isCreated());
        getAs(store, "/api/v1/purchase-orders/" + poId).andExpect(jsonPath("$.status").value("RECEIVED"));
        receive("after-" + UUID.randomUUID(), "DN-3", "1").andExpect(status().isConflict());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        submit();
        approve();
        postAs(store, "/api/v1/purchase-orders/" + poId + "/receipts", receiptBody("DN-9", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    /** A double-click sends the same request twice at once: one booking, one replay of it. */
    @Test
    void concurrentDuplicateRequestsBookOnce() throws Exception {
        submit();
        approve();
        String key = "double-click-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return receive(key, "DN-500", "3").andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        getAs(store, "/api/v1/materials/" + materialId).andExpect(jsonPath("$.quantityOnHand").value("3.000"));
    }

    private void submit() throws Exception {
        postAs(store, "/api/v1/purchase-orders/" + poId + "/submit", Map.of("version", 0)).andExpect(status().isOk());
    }

    private void approve() throws Exception {
        postAs(finance, "/api/v1/purchase-orders/" + poId + "/approve", Map.of("version", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private ResultActions receive(String key, String deliveryNote, String quantity) throws Exception {
        return mvc.perform(authed(post("/api/v1/purchase-orders/" + poId + "/receipts"), store)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(receiptBody(deliveryNote, quantity))));
    }

    private Map<String, Object> receiptBody(String deliveryNote, String quantity) {
        return Map.of("deliveryNoteRef", deliveryNote, "items", List.of(Map.of("purchaseOrderItemId", lineId, "quantity", quantity)));
    }
}
