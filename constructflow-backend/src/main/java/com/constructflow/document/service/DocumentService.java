package com.constructflow.document.service;

import com.constructflow.audit.entity.AuditAction;
import com.constructflow.audit.service.AuditService;
import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.ForbiddenException;
import com.constructflow.common.exception.NotFoundException;
import com.constructflow.common.exception.OptimisticLock;
import com.constructflow.common.response.PageResponse;
import com.constructflow.document.dto.DocumentDtos.DocumentResponse;
import com.constructflow.document.dto.DocumentDtos.DocumentVersionResponse;
import com.constructflow.document.dto.DocumentDtos.DownloadedFile;
import com.constructflow.document.entity.Document;
import com.constructflow.document.entity.DocumentVersion;
import com.constructflow.document.repository.DocumentRepository;
import com.constructflow.document.repository.DocumentVersionRepository;
import com.constructflow.document.storage.FileStorage;
import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectRole;
import com.constructflow.project.service.ProjectAccessPolicy;
import com.constructflow.project.service.ProjectMemberService;
import com.constructflow.security.CurrentUser;
import com.constructflow.user.dto.UserSummary;
import com.constructflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    static final long MAX_BYTES = 25L * 1024 * 1024;

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final FileStorage storage;
    private final ProjectAccessPolicy access;
    private final ProjectMemberService projectMembers;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final UserRepository users;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions, FileStorage storage,
                           ProjectAccessPolicy access, ProjectMemberService projectMembers,
                           NotificationService notifications, AuditService auditService, UserRepository users) {
        this.documents = documents;
        this.versions = versions;
        this.storage = storage;
        this.access = access;
        this.projectMembers = projectMembers;
        this.notifications = notifications;
        this.auditService = auditService;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(CurrentUser user, UUID projectId, Document.Category category, String q,
                                               Pageable pageable) {
        access.requireReadable(user, projectId);
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Document> page = documents.search(projectId, category, pattern, pageable);
        // One query for the latest revision of every document on the page.
        Map<UUID, DocumentVersion> latest = versions.findByDocumentIdIn(page.getContent().stream().map(Document::getId).toList())
                .stream()
                .collect(Collectors.toMap(v -> v.getDocument().getId(), Function.identity(),
                        (a, b) -> a.getVersionNo() >= b.getVersionNo() ? a : b));
        return PageResponse.of(page, page.getContent().stream().map(d -> toResponse(d, latest.get(d.getId()))).toList());
    }

    @Transactional
    public DocumentResponse upload(CurrentUser user, UUID projectId, String title, Document.Category category,
                                   MultipartFile file, String changeNote) {
        Project project = requireUploadAccess(user, projectId);
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new BadRequestException("INVALID_TITLE", "title", "Title is required (max 200 characters).");
        }
        Document document = documents.save(new Document(project, title.trim(), category, users.getReferenceById(user.userId())));
        DocumentVersion version = storeVersion(user, document, 1, file, changeNote);
        auditService.record(user, AuditAction.DOCUMENT_UPLOADED, "Document", document.getId(), projectId,
                "Uploaded '%s' (%s)".formatted(document.getTitle(), version.getOriginalFilename()),
                Map.of("sha256", version.getSha256(), "sizeBytes", version.getSizeBytes()));
        documents.flush();
        return toResponse(document, version);
    }

    @Transactional
    public DocumentResponse uploadVersion(CurrentUser user, UUID documentId, Long expectedVersion, MultipartFile file,
                                          String changeNote) {
        Document document = load(user, documentId);
        requireUploadAccess(user, document.getProject().getId());
        OptimisticLock.check(expectedVersion, document.getVersion(), "Document");
        int versionNo = document.nextVersion();
        DocumentVersion version = storeVersion(user, document, versionNo, file, changeNote);
        documents.flush();
        auditService.record(user, AuditAction.DOCUMENT_VERSION_UPLOADED, "Document", document.getId(),
                document.getProject().getId(), "Uploaded version %d of '%s'".formatted(versionNo, document.getTitle()),
                Map.of("versionNo", versionNo, "sha256", version.getSha256()));
        notifications.notifyAll(user.organizationId(),
                projectMembers.userIdsWithRole(document.getProject().getId(), ProjectRole.MANAGER), user.userId(),
                NotificationType.DOCUMENT_UPDATED, "New version of " + document.getTitle(),
                "Version %d uploaded".formatted(versionNo), "/projects/%s/documents".formatted(document.getProject().getId()));
        return toResponse(document, version);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> versions(CurrentUser user, UUID documentId) {
        Document document = load(user, documentId);
        return versions.findByDocumentIdOrderByVersionNoDesc(document.getId()).stream()
                .map(DocumentService::toVersionResponse).toList();
    }

    /** Downloads go through the API after a permission check; storage is never exposed directly. */
    @Transactional(readOnly = true)
    public DownloadedFile download(CurrentUser user, UUID documentId, Integer versionNo) {
        Document document = load(user, documentId);
        int number = versionNo != null ? versionNo : document.getCurrentVersionNo();
        DocumentVersion version = versions.findByDocumentIdAndVersionNo(document.getId(), number)
                .orElseThrow(() -> new NotFoundException("Document version"));
        return new DownloadedFile(storage.open(version.getStorageKey()), version.getOriginalFilename(),
                version.getContentType(), version.getSizeBytes());
    }

    private Project requireUploadAccess(CurrentUser user, UUID projectId) {
        Project project = access.requireReadable(user, projectId);
        if (!user.isAdmin() && !access.isMember(user, project)) {
            throw new ForbiddenException("NOT_PROJECT_MEMBER", "Only project members can upload documents.");
        }
        access.requireOpen(project);
        return project;
    }

    private DocumentVersion storeVersion(CurrentUser user, Document document, int versionNo, MultipartFile file,
                                         String changeNote) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "file", "Choose a file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("FILE_TOO_LARGE", "file", "The file exceeds the 25 MB limit.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String displayName = sanitizeFilename(file.getOriginalFilename());
        FileTypeDetector.DetectedType type = FileTypeDetector.detect(content, displayName);
        String key = "%s/%s/%s".formatted(document.getOrganizationId(), document.getId(), UUID.randomUUID());
        storage.store(key, content);
        // If the database transaction later rolls back, remove the file so no orphan bytes remain.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.delete(key);
                    }
                }
            });
        }
        return versions.save(new DocumentVersion(document, versionNo, key, displayName, type.contentType(),
                content.length, sha256(content), blank(changeNote), users.getReferenceById(user.userId())));
    }

    private Document load(CurrentUser user, UUID documentId) {
        Document document = documents.findByIdAndOrganizationId(documentId, user.organizationId())
                .orElseThrow(() -> new NotFoundException("Document"));
        access.requireReadable(user, document.getProject().getId());
        return document;
    }

    /** Keeps the name readable for display while stripping path separators and control characters. */
    static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return "file";
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DocumentResponse toResponse(Document d, DocumentVersion latest) {
        return new DocumentResponse(d.getId(), d.getProject().getId(), d.getTitle(), d.getCategory(),
                d.getCurrentVersionNo(), latest == null ? null : toVersionResponse(latest),
                UserSummary.of(d.getCreatedBy()), d.getVersion(), d.getUpdatedAt());
    }

    private static DocumentVersionResponse toVersionResponse(DocumentVersion v) {
        return new DocumentVersionResponse(v.getId(), v.getVersionNo(), v.getOriginalFilename(), v.getContentType(),
                v.getSizeBytes(), v.getSha256(), v.getChangeNote(), UserSummary.of(v.getUploadedBy()), v.getUploadedAt());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
