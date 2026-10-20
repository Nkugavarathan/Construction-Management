package com.constructflow.common.exception;

import org.springframework.http.HttpStatus;

/** Authentication failed or the session is no longer valid. Messages stay deliberately generic. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
