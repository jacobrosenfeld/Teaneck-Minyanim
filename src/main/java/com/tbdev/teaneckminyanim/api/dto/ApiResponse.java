package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standard wrapper for all API responses.
 * - data: the actual payload
 * - meta: pagination, window bounds, counts, etc. (omitted when null)
 * - error: error details for non-2xx responses (omitted when null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Map<String, Object> meta, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, meta, null);
    }

    public static <T> ApiResponse<T> err(String code, String message) {
        return new ApiResponse<>(null, null, new ApiError(code, message));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message) {}
}
