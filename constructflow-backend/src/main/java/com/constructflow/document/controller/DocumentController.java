package com.constructflow.document.controller;

import com.constructflow.common.response.PageResponse;
import com.constructflow.document.dto.DocumentDtos.DocumentResponse;
import com.constructflow.document.dto.DocumentDtos.DocumentVersionResponse;
import com.constructflow.document.dto.DocumentDtos.DownloadedFile;
import com.constructflow.document.entity.Document;
import com.constructflow.document.service.DocumentService;
import com.constructflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/projects/{projectId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public PageResponse<DocumentResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                               @RequestParam(required = false) Document.Category category,
                                               @RequestParam(required = false) String q,
                                               @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.list(user, projectId, category, q, pageable);
    }

    @PostMapping(value = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentResponse upload(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId,
                                   @RequestParam String title, @RequestParam Document.Category category,
                                   @RequestParam(required = false) String changeNote,
                                   @RequestPart("file") MultipartFile file) {
        return documentService.upload(user, projectId, title, category, file, changeNote);
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentResponse uploadVersion(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID documentId,
                                          @RequestParam Long version,
                                          @RequestParam(required = false) String changeNote,
                                          @RequestPart("file") MultipartFile file) {
        return documentService.uploadVersion(user, documentId, version, file, changeNote);
    }

    @GetMapping("/documents/{documentId}/versions")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public List<DocumentVersionResponse> versions(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID documentId) {
        return documentService.versions(user, documentId);
    }

    /** Always served as an attachment with nosniff, so an uploaded file is never rendered inline by the browser. */
    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable UUID documentId,
                                                        @RequestParam(required = false) Integer versionNo) {
        DownloadedFile file = documentService.download(user, documentId, versionNo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.content()));
    }
}
