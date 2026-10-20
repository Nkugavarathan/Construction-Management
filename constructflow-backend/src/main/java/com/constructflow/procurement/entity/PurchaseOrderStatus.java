package com.constructflow.procurement.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * DRAFT -> SUBMITTED -> APPROVED -> PARTIALLY_RECEIVED -> RECEIVED.
 * SUBMITTED -> DRAFT (returned for changes); DRAFT/SUBMITTED/APPROVED -> CANCELLED (never after goods
 * arrived); PARTIALLY_RECEIVED -> CLOSED (supplier cannot deliver the remainder).
 * Receiving transitions happen only through goods receipts, never by a direct status change.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,
    CLOSED;

    public Set<PurchaseOrderStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(APPROVED, DRAFT, CANCELLED);
            case APPROVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED);
            case PARTIALLY_RECEIVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CLOSED);
            case RECEIVED, CANCELLED, CLOSED -> EnumSet.noneOf(PurchaseOrderStatus.class);
        };
    }

    public boolean canReceive() {
        return this == APPROVED || this == PARTIALLY_RECEIVED;
    }
}
