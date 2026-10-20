package com.constructflow.project.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Project lifecycle (blueprint section 11.1). The allowed transitions live here, next to the states,
 * so the rule exists in exactly one place and can be unit-tested exhaustively.
 */
public enum ProjectStatus {
    PLANNING,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    public Set<ProjectStatus> allowedTargets() {
        return switch (this) {
            case PLANNING -> EnumSet.of(ACTIVE, CANCELLED);
            case ACTIVE -> EnumSet.of(ON_HOLD, COMPLETED, CANCELLED);
            case ON_HOLD -> EnumSet.of(ACTIVE, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(ProjectStatus.class);
        };
    }

    public boolean canTransitionTo(ProjectStatus target) {
        return allowedTargets().contains(target);
    }

    /** Completed and cancelled projects are read-only. */
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean requiresReason() {
        return this == ON_HOLD || this == CANCELLED;
    }
}
