package com.constructflow.task.repository;

import com.constructflow.task.entity.SiteReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface SiteReportRepository extends JpaRepository<SiteReport, UUID> {

    @EntityGraph(attributePaths = "author")
    Page<SiteReport> findByProjectId(UUID projectId, Pageable pageable);

    boolean existsByProjectIdAndAuthorIdAndReportDate(UUID projectId, UUID authorId, LocalDate reportDate);
}
