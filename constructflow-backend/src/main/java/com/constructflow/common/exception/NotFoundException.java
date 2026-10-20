package com.constructflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Resource does not exist <em>or belongs to another organization</em>. Both cases produce the same
 * response so that callers cannot probe for other tenants' identifiers.
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String resourceName) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resourceName + " was not found.");
    }
}
