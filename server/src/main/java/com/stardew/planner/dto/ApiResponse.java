package com.stardew.planner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应格式
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String errorCode;
    private Object details;

    public ApiResponse(int code, String message, T data, String errorCode, Object details) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.details = details;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, null, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "created", data, null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, Object details) {
        return new ApiResponse<>(400, message, null, errorCode, details);
    }

    public static <T> ApiResponse<T> error(int httpCode, String errorCode, String message, Object details) {
        return new ApiResponse<>(httpCode, message, null, errorCode, details);
    }
}
