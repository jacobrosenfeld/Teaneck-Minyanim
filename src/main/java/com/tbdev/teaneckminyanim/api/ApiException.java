package com.tbdev.teaneckminyanim.api;

import org.springframework.http.HttpStatus;

/**
 * An API error with an HTTP status and a stable machine-readable code. {@link ApiExceptionHandler}
 * renders it as {@code ApiResponse.err(code, message)}. The message is shown to API clients, so it
 * must never contain a token, key or other secret.
 */
public class ApiException extends RuntimeException {
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INSUFFICIENT_SCOPE = "INSUFFICIENT_SCOPE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_MANUAL_OVERRIDE = "NOT_MANUAL_OVERRIDE";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
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

    /** 400 VALIDATION_ERROR. */
    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message);
    }

    /** 401 UNAUTHENTICATED: no bearer credentials on the request. */
    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, UNAUTHENTICATED,
                "Authentication required. Send Authorization: Bearer <token>.");
    }

    /** 401 INVALID_TOKEN: bearer credentials present but unusable. */
    public static ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN,
                "The bearer token is invalid, expired or revoked.");
    }

    /** 403 FORBIDDEN. */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN, message);
    }

    /** 403 INSUFFICIENT_SCOPE, naming the scope wire value that was required. */
    public static ApiException insufficientScope(String requiredScope) {
        return new ApiException(HttpStatus.FORBIDDEN, INSUFFICIENT_SCOPE,
                "This API key does not have the " + requiredScope + " scope.");
    }

    /** 404 NOT_FOUND. */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, message);
    }

    /** 409 with the given code, e.g. NOT_MANUAL_OVERRIDE. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
