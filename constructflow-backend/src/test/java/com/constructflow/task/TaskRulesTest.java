package com.constructflow.task;

import com.constructflow.common.exception.BadRequestException;
import com.constructflow.common.exception.InvalidTransitionException;
import com.constructflow.task.entity.Task;
import com.constructflow.task.entity.TaskPriority;
import com.constructflow.task.entity.TaskStatus;
import com.constructflow.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;

import static com.constructflow.task.entity.TaskStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void doneSetsCompletedAtAndReopenClearsIt() {
        Task task = task(null, null);
        task.transitionTo(IN_PROGRESS, null, Instant.EPOCH);
        task.transitionTo(DONE, null, Instant.parse("2026-10-20T10:00:00Z"));
        assertThat(task.getCompletedAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
        task.transitionTo(IN_PROGRESS, null, Instant.EPOCH);
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void blockedCannotJumpStraightToDone() {
        Task task = task(null, null);
        task.transitionTo(IN_PROGRESS, null, Instant.EPOCH);
        task.transitionTo(BLOCKED, "waiting for crane", Instant.EPOCH);
        assertThatThrownBy(() -> task.transitionTo(DONE, null, Instant.EPOCH)).isInstanceOf(InvalidTransitionException.class);
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,CANCELLED,true", "DONE,IN_PROGRESS,true", "CANCELLED,TODO,true",
            "TODO,IN_PROGRESS,false", "IN_PROGRESS,DONE,false", "IN_PROGRESS,BLOCKED,false"})
    void managerOnlyTransitions(TaskStatus from, TaskStatus to, boolean managerOnly) {
        assertThat(TaskStatus.requiresManager(from, to)).isEqualTo(managerOnly);
    }

    @Test
    void overdueOnlyWhenOpenAndPastDue() {
        assertThat(task(null, TODAY.minusDays(1)).isOverdue(TODAY)).isTrue();
        assertThat(task(null, TODAY).isOverdue(TODAY)).isFalse();
        assertThat(task(null, null).isOverdue(TODAY)).isFalse();
        Task done = task(null, TODAY.minusDays(5));
        done.transitionTo(DONE, null, Instant.EPOCH);
        assertThat(done.isOverdue(TODAY)).isFalse();
    }

    @Test
    void dueDateBeforeStartIsRejected() {
        assertThatThrownBy(() -> task(TODAY, TODAY.minusDays(1))).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0", "9,23,39", "10,10,100", "1,3,33", "2,3,66"})
    void weightedProgressRoundsDown(long done, long total, int expected) {
        assertThat(TaskService.progressPercent(done, total)).isEqualTo(expected);
    }

    private static Task task(LocalDate start, LocalDate due) {
        return new Task(null, null, "Pour slab", null, null, TaskPriority.HIGH, 1, start, due, null);
    }
}
