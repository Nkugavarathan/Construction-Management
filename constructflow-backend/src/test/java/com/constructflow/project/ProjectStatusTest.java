package com.constructflow.project;

import com.constructflow.common.exception.InvalidTransitionException;
import com.constructflow.project.entity.Project;
import com.constructflow.project.entity.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.constructflow.project.entity.ProjectStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PRJ-2: the lifecycle graph from blueprint 11.1, checked for every (from, to) pair. */
class ProjectStatusTest {

    private static final Map<ProjectStatus, Set<ProjectStatus>> EXPECTED = Map.of(
            PLANNING, EnumSet.of(ACTIVE, CANCELLED),
            ACTIVE, EnumSet.of(ON_HOLD, COMPLETED, CANCELLED),
            ON_HOLD, EnumSet.of(ACTIVE, CANCELLED),
            COMPLETED, EnumSet.noneOf(ProjectStatus.class),
            CANCELLED, EnumSet.noneOf(ProjectStatus.class));

    @ParameterizedTest
    @EnumSource(ProjectStatus.class)
    void allowsExactlyTheDocumentedTransitions(ProjectStatus from) {
        for (ProjectStatus to : ProjectStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @Test
    void closedStatusesAreReadOnly() {
        assertThat(COMPLETED.isClosed()).isTrue();
        assertThat(CANCELLED.isClosed()).isTrue();
        assertThat(ACTIVE.isClosed()).isFalse();
    }

    @Test
    void completingSetsActualEndDate() {
        Project project = project();
        project.transitionTo(ACTIVE, null, LocalDate.of(2026, 1, 1));
        project.transitionTo(COMPLETED, null, LocalDate.of(2026, 10, 20));
        assertThat(project.getActualEndDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void invalidTransitionReportsAllowedTargets() {
        Project project = project();
        assertThatThrownBy(() -> project.transitionTo(COMPLETED, null, LocalDate.now()))
                .isInstanceOf(InvalidTransitionException.class)
                .satisfies(ex -> assertThat(((InvalidTransitionException) ex).getProperties().get("allowedTransitions"))
                        .isEqualTo(java.util.List.of("ACTIVE", "CANCELLED")));
    }

    private static Project project() {
        return new Project(UUID.randomUUID(), "P-1", "Test", null, "Client", null, "Site",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.TEN, null);
    }
}
