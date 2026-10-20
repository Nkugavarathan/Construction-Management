package com.constructflow.task.repository;

import com.constructflow.task.entity.Task;
import com.constructflow.task.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    /** Tenant check through the parent project: the task's organization is its project's organization. */
    @EntityGraph(attributePaths = {"project", "assignee", "milestone"})
    @Query("select t from Task t where t.id = :id and t.project.organizationId = :organizationId")
    Optional<Task> findInOrganization(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @Override
    @EntityGraph(attributePaths = {"project", "assignee", "milestone"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    long countByProjectIdAndStatusIn(UUID projectId, Collection<TaskStatus> statuses);

    long countByProjectIdAndAssigneeIdAndStatusIn(UUID projectId, UUID assigneeId, Collection<TaskStatus> statuses);

    long countByMilestoneIdAndStatusIn(UUID milestoneId, Collection<TaskStatus> statuses);

    /**
     * Weighted progress inputs for many projects in ONE grouped query (not one query per project).
     * Cancelled tasks are excluded from both numerator and denominator.
     */
    @Query("""
            select t.project.id as groupId,
                   sum(case when t.status = com.constructflow.task.entity.TaskStatus.DONE then t.weight else 0 end) as doneWeight,
                   sum(t.weight) as totalWeight
            from Task t
            where t.project.id in :projectIds and t.status <> com.constructflow.task.entity.TaskStatus.CANCELLED
            group by t.project.id
            """)
    List<ProgressRow> progressByProject(@Param("projectIds") Collection<UUID> projectIds);

    @Query("""
            select t.milestone.id as groupId,
                   sum(case when t.status = com.constructflow.task.entity.TaskStatus.DONE then t.weight else 0 end) as doneWeight,
                   sum(t.weight) as totalWeight
            from Task t
            where t.milestone.id in :milestoneIds and t.status <> com.constructflow.task.entity.TaskStatus.CANCELLED
            group by t.milestone.id
            """)
    List<ProgressRow> progressByMilestone(@Param("milestoneIds") Collection<UUID> milestoneIds);

    @Query("""
            select count(t) from Task t
            where t.project.id in :projectIds and t.dueDate < :today
              and t.status in (com.constructflow.task.entity.TaskStatus.TODO,
                               com.constructflow.task.entity.TaskStatus.IN_PROGRESS,
                               com.constructflow.task.entity.TaskStatus.BLOCKED)
            """)
    long countOverdue(@Param("projectIds") Collection<UUID> projectIds, @Param("today") LocalDate today);

    @Query("select t.status as status, count(t) as total from Task t where t.project.id = :projectId group by t.status")
    List<StatusCount> countByStatus(@Param("projectId") UUID projectId);

    /** Used by the daily reminder job across all organizations. */
    @EntityGraph(attributePaths = {"project", "assignee"})
    @Query("""
            select t from Task t
            where t.assignee is not null and t.dueDate is not null and t.dueDate <= :until
              and t.status in (com.constructflow.task.entity.TaskStatus.TODO,
                               com.constructflow.task.entity.TaskStatus.IN_PROGRESS,
                               com.constructflow.task.entity.TaskStatus.BLOCKED)
            """)
    List<Task> findOpenDueOnOrBefore(@Param("until") LocalDate until);

    interface ProgressRow {
        UUID getGroupId();

        Number getDoneWeight();

        Number getTotalWeight();
    }

    interface StatusCount {
        TaskStatus getStatus();

        long getTotal();
    }
}
