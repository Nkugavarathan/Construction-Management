package com.constructflow.common.exception;

import java.util.Collection;

/** A state-machine transition that the current status does not allow. Lists the allowed targets. */
public class InvalidTransitionException extends ConflictException {

    public InvalidTransitionException(String entityName, Enum<?> from, Enum<?> to, Collection<? extends Enum<?>> allowed) {
        super("INVALID_TRANSITION", "%s cannot move from %s to %s.".formatted(entityName, from, to));
        addProperty("currentStatus", from.name());
        addProperty("allowedTransitions", allowed.stream().map(Enum::name).sorted().toList());
    }
}
