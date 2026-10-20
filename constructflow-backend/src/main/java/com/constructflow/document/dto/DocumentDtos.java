package com.constructflow.document.dto;

import com.constructflow.document.entity.Document;
import com.constructflow.user.dto.UserSummary;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentVersionResponse(UUID id, int versionNo, String originalFilename, String contentType,
                                          long sizeBytes, String sha256, String changeNote, UserSummary uploadedBy,
                                          Instant uploadedAt) {
    }

    public record DocumentResponse(UUID id, UUID projectId, String title, Document.Category category,
                                   int currentVersionNo, DocumentVersionResponse latest, UserSummary createdBy,
                                   Long version, Instant updatedAt) {
    }

    public record DownloadedFile(InputStream content, String filename, String contentType, long sizeBytes) {
    }
}
