package com.constructflow.common.exception;

import org.springframework.http.HttpStatus;

/** Authenticated, but this user may not perform this action on this resource. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
