package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

/**
 * Renders errors from {@code /api} controllers in the {@code ApiResponse} envelope. Controllers that already
 * return their own error responses are unaffected; this only sees exceptions they let escape.
 * <ul>
 *   <li>{@link ApiException}: its status and code.</li>
 *   <li>Binding, validation and parse errors: 400 VALIDATION_ERROR.</li>
 *   <li>{@link AccessDeniedException}: 403 FORBIDDEN. {@link AuthenticationException}: 401 UNAUTHENTICATED.</li>
 *   <li>Other Spring MVC errors ({@link ErrorResponse}) keep their status (404 NOT_FOUND, 405, 415, ...).</li>
 *   <li>Anything else: 500 INTERNAL_ERROR, logged, with no stack trace or exception message in the body.</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.tbdev.teaneckminyanim.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(org.springframework.web.bind.MethodArgumentNotValidException e) {
        return validation(bindingMessage(e));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException e) {
        return validation(bindingMessage(e));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return validation(message.isBlank() ? "Request validation failed." : message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation("Invalid value for '" + e.getName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return validation("Missing required parameter '" + e.getParameterName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return validation("Missing required part '" + e.getRequestPartName() + "'.");
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(ServletRequestBindingException e) {
        return validation("The request is missing a required value.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return validation("The request body is missing or is not valid JSON for this endpoint.");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDateTimeParse(DateTimeParseException e) {
        return validation("Invalid date or time. Use YYYY-MM-DD for dates and HH:mm for times.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, ApiException.FORBIDDEN,
                e.getMessage() == null || e.getMessage().isBlank() ? "Access denied." : e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        return error(HttpStatus.UNAUTHORIZED, ApiException.UNAUTHENTICATED, "Authentication required.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            return springError(e, errorResponse.getStatusCode());
        }
        log.error("Unhandled API error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ApiException.INTERNAL_ERROR, "An unexpected error occurred.");
    }

    /** Spring MVC's own exceptions (405, 406, 415, 404 for missing resources, ...) keep their status. */
    private static ResponseEntity<ApiResponse<Void>> springError(Exception e, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            log.error("API request failed", e);
            return error(status, ApiException.INTERNAL_ERROR, "An unexpected error occurred.");
        }
        String code = switch (status.value()) {
            case 400 -> ApiException.VALIDATION_ERROR;
            case 401 -> ApiException.UNAUTHENTICATED;
            case 403 -> ApiException.FORBIDDEN;
            case 404 -> ApiException.NOT_FOUND;
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> ApiException.RATE_LIMITED;
            default -> "ERROR";
        };
        String message = status.value() == 404 ? "Not found." : "The request could not be processed.";
        return error(status, code, message);
    }

    private static String bindingMessage(BindException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof org.springframework.validation.FieldError field
                        ? field.getField() + " " + (field.getDefaultMessage() == null ? "is invalid" : field.getDefaultMessage())
                        : (error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return message.isBlank() ? "Request validation failed." : message;
    }

    private static ResponseEntity<ApiResponse<Void>> validation(String message) {
        return error(HttpStatus.BAD_REQUEST, ApiException.VALIDATION_ERROR, message);
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatusCode status, String code, String message) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return builder.body(ApiResponse.err(code, message));
    }
}
