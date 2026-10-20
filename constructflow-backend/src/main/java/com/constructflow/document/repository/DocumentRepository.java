package com.constructflow.document.repository;

import com.constructflow.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @EntityGraph(attributePaths = {"project", "createdBy"})
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = "createdBy")
    @Query("""
            select d from Document d where d.project.id = :projectId
              and (:category is null or d.category = :category)
              and (:q is null or lower(d.title) like :q)
            """)
    Page<Document> search(@Param("projectId") UUID projectId, @Param("category") Document.Category category,
                          @Param("q") String q, Pageable pageable);
}
