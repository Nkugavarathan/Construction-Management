package com.constructflow.document.repository;

import com.constructflow.document.entity.DocumentVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNo(UUID documentId, int versionNo);

    @EntityGraph(attributePaths = "uploadedBy")
    List<DocumentVersion> findByDocumentIdIn(Collection<UUID> documentIds);
}
