package com.constructflow.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for expected business failures. Each carries the HTTP status it maps to and a stable,
 * machine-readable {@code code} that the frontend can switch on. The message is safe to show to users.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    protected void addProperty(String key, Object value) {
        properties.put(key, value);
    }
}
