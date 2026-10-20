package com.constructflow.common.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Input passed Bean Validation but breaks a business rule that depends on other data
 * (for example "budget allocations exceed the approved budget"). When {@code field} is set the
 * error is reported in the same {@code errors[]} shape as Bean Validation failures.
 */
public class BadRequestException extends DomainException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String code, String field, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
        addProperty("errors", List.of(Map.of("field", field, "message", message)));
    }
}
