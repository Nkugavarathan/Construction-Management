package com.constructflow.task.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Task lifecycle (blueprint section 11.2):
 * TODO -> IN_PROGRESS -> DONE, IN_PROGRESS <-> BLOCKED, anything open -> CANCELLED,
 * DONE -> IN_PROGRESS (reopen) and CANCELLED -> TODO (restore).
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    DONE,
    CANCELLED;

    public static final Set<TaskStatus> OPEN = EnumSet.of(TODO, IN_PROGRESS, BLOCKED);

    public Set<TaskStatus> allowedTargets() {
        return switch (this) {
            case TODO -> EnumSet.of(IN_PROGRESS, DONE, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(TODO, BLOCKED, DONE, CANCELLED);
            case BLOCKED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case DONE -> EnumSet.of(IN_PROGRESS);
            case CANCELLED -> EnumSet.of(TODO);
        };
    }

    public boolean canTransitionTo(TaskStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    /**
     * Cancelling, reopening a finished task and restoring a cancelled one change the plan, so only
     * project managers may do them. Assignees may move their own work forward.
     */
    public static boolean requiresManager(TaskStatus from, TaskStatus to) {
        return to == CANCELLED || from == DONE || from == CANCELLED;
    }
}
