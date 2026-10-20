package com.constructflow.domain;

import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ConflictException;
import com.constructflow.document.service.FileTypeDetector;
import com.constructflow.inventory.entity.InsufficientStockException;
import com.constructflow.inventory.entity.Material;
import com.constructflow.inventory.entity.MaterialUnit;
import com.constructflow.procurement.entity.PurchaseOrderItem;
import com.constructflow.procurement.entity.PurchaseOrderStatus;
import com.constructflow.risk.entity.Risk;
import com.constructflow.safety.entity.SafetyIncident;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules that live on entities and small helpers, tested without Spring or a database. */
class DomainRulesTest {

    // ---- Inventory --------------------------------------------------------------------------

    @Test
    void stockNeverGoesNegative() {
        Material cement = new Material(UUID.randomUUID(), "CEM", "Cement", MaterialUnit.BAG, new BigDecimal("10"));
        cement.applyMovement(new BigDecimal("25"));
        assertThat(cement.applyMovement(new BigDecimal("-25"))).isEqualByComparingTo("0");
        assertThatThrownBy(() -> cement.applyMovement(new BigDecimal("-0.001")))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(cement.getQuantityOnHand()).isEqualByComparingTo("0");
    }

    @Test
    void lowStockAtOrBelowReorderLevel() {
        Material m = new Material(UUID.randomUUID(), "SAND", "Sand", MaterialUnit.M3, new BigDecimal("20"));
        m.applyMovement(new BigDecimal("21"));
        assertThat(m.isLowStock()).isFalse();
        m.applyMovement(new BigDecimal("-1"));
        assertThat(m.isLowStock()).isTrue();
    }

    @Test
    void unitCannotChangeWhileHoldingStock() {
        Material m = new Material(UUID.randomUUID(), "SAND", "Sand", MaterialUnit.M3, BigDecimal.ONE);
        m.applyMovement(BigDecimal.TEN);
        assertThatThrownBy(() -> m.update("Sand", MaterialUnit.TONNE, BigDecimal.ONE, true)).isInstanceOf(ConflictException.class);
    }

    // ---- Procurement ------------------------------------------------------------------------

    @Test
    void lineTotalRoundsHalfEvenToCents() {
        Material m = new Material(UUID.randomUUID(), "X", "X", MaterialUnit.KG, BigDecimal.ZERO);
        assertThat(new PurchaseOrderItem(m, new BigDecimal("3"), new BigDecimal("0.1250")).getLineTotal())
                .isEqualByComparingTo("0.38");  // 0.375 -> 0.38 (half-even rounds to the even digit 8)
        assertThat(new PurchaseOrderItem(m, new BigDecimal("1"), new BigDecimal("0.1250")).getLineTotal())
                .isEqualByComparingTo("0.12");  // 0.125 -> 0.12
    }

    @Test
    void cannotReceiveMoreThanOutstanding() {
        Material m = new Material(UUID.randomUUID(), "STL", "Steel", MaterialUnit.TONNE, BigDecimal.ZERO);
        PurchaseOrderItem item = new PurchaseOrderItem(m, new BigDecimal("10"), BigDecimal.ONE);
        item.receive(new BigDecimal("6"));
        assertThat(item.remaining()).isEqualByComparingTo("4");
        assertThatThrownBy(() -> item.receive(new BigDecimal("4.001"))).hasFieldOrPropertyWithValue("code", "OVER_RECEIPT");
        item.receive(new BigDecimal("4"));
        assertThat(item.isFullyReceived()).isTrue();
    }

    @Test
    void receivingOnlyAfterApproval() {
        assertThat(PurchaseOrderStatus.DRAFT.canReceive()).isFalse();
        assertThat(PurchaseOrderStatus.SUBMITTED.canReceive()).isFalse();
        assertThat(PurchaseOrderStatus.APPROVED.canReceive()).isTrue();
        assertThat(PurchaseOrderStatus.PARTIALLY_RECEIVED.canReceive()).isTrue();
        assertThat(PurchaseOrderStatus.RECEIVED.allowedTargets()).isEmpty();
    }

    // ---- Risk & safety ------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"1,1,1,LOW", "2,2,4,LOW", "1,5,5,MEDIUM", "3,3,9,MEDIUM", "2,5,10,HIGH", "4,4,16,HIGH",
            "3,6,-1,ERR", "0,3,-1,ERR", "5,5,25,CRITICAL", "4,5,20,CRITICAL"})
    void riskScoreAndLevel(int likelihood, int impact, int expectedScore, String expectedLevel) {
        if ("ERR".equals(expectedLevel)) {
            assertThatThrownBy(() -> Risk.score(likelihood, impact)).isInstanceOf(BadRequestException.class);
            return;
        }
        int score = Risk.score(likelihood, impact);
        assertThat(score).isEqualTo(expectedScore);
        assertThat(Risk.Level.of(score).name()).isEqualTo(expectedLevel);
    }

    @Test
    void incidentCannotCloseWithoutCorrectiveActions() {
        SafetyIncident incident = new SafetyIncident(null, Instant.now(), "Gate", SafetyIncident.Severity.MINOR, "Slip", null);
        assertThatThrownBy(() -> incident.transitionTo(SafetyIncident.Status.CLOSED, null, Instant.now()))
                .hasFieldOrPropertyWithValue("code", "CORRECTIVE_ACTIONS_REQUIRED");
        incident.updateInvestigation(SafetyIncident.Severity.MINOR, "Anti-slip mats installed");
        incident.transitionTo(SafetyIncident.Status.CLOSED, null, Instant.now());
        assertThat(incident.getStatus()).isEqualTo(SafetyIncident.Status.CLOSED);
    }

    // ---- Uploads -------------------------------------------------------------------------------

    @Test
    void fileTypeComesFromContentNotName() {
        byte[] pdf = "%PDF-1.7 rest".getBytes();
        assertThat(FileTypeDetector.detect(pdf, "drawing.pdf").contentType()).isEqualTo("application/pdf");
        // A PDF renamed to .png is still detected as PDF.
        assertThat(FileTypeDetector.detect(pdf, "innocent.png").contentType()).isEqualTo("application/pdf");
        // An HTML/script payload named .pdf is rejected.
        assertThatThrownBy(() -> FileTypeDetector.detect("<script>alert(1)</script>".getBytes(), "report.pdf"))
                .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_FILE_TYPE");
        // A ZIP is only accepted as DOCX/XLSX, never as an arbitrary archive.
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0, 0};
        assertThat(FileTypeDetector.detect(zip, "boq.xlsx").extension()).isEqualTo("xlsx");
        assertThatThrownBy(() -> FileTypeDetector.detect(zip, "payload.zip")).isInstanceOf(BadRequestException.class);
    }
}
