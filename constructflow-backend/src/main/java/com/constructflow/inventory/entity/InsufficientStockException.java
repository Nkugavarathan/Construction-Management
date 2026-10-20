package com.constructflow.inventory.entity;

import com.constructflow.common.exception.ConflictException;

import java.math.BigDecimal;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String materialCode, BigDecimal available, BigDecimal requested) {
        super("INSUFFICIENT_STOCK", "Only %s of %s is in stock; %s was requested."
                .formatted(available.stripTrailingZeros().toPlainString(), materialCode,
                        requested.stripTrailingZeros().toPlainString()));
        addProperty("available", available);
        addProperty("requested", requested);
    }
}
