package com.carepoint.common.error;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fields) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, null);
    }

    public static ApiError of(ErrorCode code, String message, Map<String, String> fields) {
        return new ApiError(code.name(), message, fields);
    }
}
