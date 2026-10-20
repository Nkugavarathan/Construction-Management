package com.constructflow.common.exception;

import org.springframework.http.HttpStatus;

/** The request is valid but conflicts with the current state (duplicate, wrong status, stale version). */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
