package com.constructflow.document.entity;

import com.constructflow.common.persistence.BaseEntity;
import com.constructflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One uploaded revision. Never modified: a new upload adds a new row and earlier revisions stay available. */
@Entity
@Immutable
@Table(name = "document_versions")
public class DocumentVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String storageKey;

    /** Shown to users and used in the download file name; never used as a filesystem path. */
    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    private String changeNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    protected DocumentVersion() {
    }

    public DocumentVersion(Document document, int versionNo, String storageKey, String originalFilename,
                           String contentType, long sizeBytes, String sha256, String changeNote, User uploadedBy) {
        this.document = document;
        this.versionNo = versionNo;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.changeNote = changeNote;
        this.uploadedBy = uploadedBy;
    }

    public Document getDocument() {
        return document;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
