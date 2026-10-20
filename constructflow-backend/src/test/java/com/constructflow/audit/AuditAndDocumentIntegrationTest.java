package com.constructflow.audit;

import com.constructflow.project.dto.ProjectDtos.ProjectResponse;
import com.constructflow.security.Role;
import com.constructflow.support.IntegrationTest;
import com.constructflow.support.TestFixtures.Org;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AUD-1 (append-only audit log), Flyway migrations, and document upload/version/download security. */
class AuditAndDocumentIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void auditEventsCannotBeUpdatedOrDeletedEvenWithDirectSql() {
        fixtures.organization();
        assertThatThrownBy(() -> jdbc.sql("update audit_events set summary = 'tampered'").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_events").update())
                .hasMessageContaining("append-only");
    }

    @Test
    void allMigrationsApplied() {
        Integer applied = jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single();
        assertThat(applied).isGreaterThanOrEqualTo(8);
    }

    @Test
    void onlyAdminsReadTheAuditLog() throws Exception {
        Org org = fixtures.organization();
        getAs(org.user(Role.ADMIN), "/api/v1/audit-events").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action").isNotEmpty());
        getAs(org.user(Role.FINANCE_OFFICER), "/api/v1/audit-events").andExpect(status().isForbidden());
    }

    @Test
    void documentsAreVersionedTypeCheckedAndDownloadedOnlyByAuthorizedUsers() throws Exception {
        Org org = fixtures.organization();
        Org other = fixtures.organization();
        ProjectResponse project = fixtures.activeProject(org, new BigDecimal("1000.00"));
        byte[] pdfV1 = "%PDF-1.7 version one".getBytes();
        byte[] pdfV2 = "%PDF-1.7 version two".getBytes();

        String documentId = read(mvc.perform(authed(multipart("/api/v1/projects/" + project.id() + "/documents")
                        .file(new MockMultipartFile("file", "../../etc/plan.pdf", "application/pdf", pdfV1))
                        .param("title", "Ground floor plan").param("category", "DRAWING"), org.user(Role.SITE_ENGINEER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latest.originalFilename").value("plan.pdf"))
                .andExpect(jsonPath("$.latest.contentType").value("application/pdf")), "$.id");

        mvc.perform(authed(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(new MockMultipartFile("file", "plan-rev-b.pdf", "application/pdf", pdfV2))
                        .param("version", "0").param("changeNote", "Rev B"), org.user(Role.SITE_ENGINEER)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currentVersionNo").value(2));

        getAs(org.user(Role.VIEWER), "/api/v1/documents/" + documentId + "/versions")
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(authed(get("/api/v1/documents/" + documentId + "/download?versionNo=1"), org.user(Role.VIEWER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(pdfV1));

        // Script content disguised as a PDF is rejected by content sniffing.
        mvc.perform(authed(multipart("/api/v1/projects/" + project.id() + "/documents")
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "<html><script>x</script>".getBytes()))
                        .param("title", "Evil").param("category", "INVOICE"), org.user(Role.SITE_ENGINEER)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        // Viewers cannot upload; other organizations cannot even see the document.
        mvc.perform(authed(multipart("/api/v1/projects/" + project.id() + "/documents")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", pdfV1))
                        .param("title", "x").param("category", "OTHER"), org.user(Role.VIEWER)))
                .andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/documents/" + documentId + "/download"), other.user(Role.ADMIN)))
                .andExpect(status().isNotFound());
    }
}
