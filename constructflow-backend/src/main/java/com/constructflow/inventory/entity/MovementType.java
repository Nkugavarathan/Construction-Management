package com.constructflow.inventory.entity;

/** RECEIPT and RETURN add stock, ISSUE removes it (to a project), ADJUSTMENT may go either way and needs a reason. */
public enum MovementType {
    RECEIPT,
    ISSUE,
    RETURN,
    ADJUSTMENT
}
