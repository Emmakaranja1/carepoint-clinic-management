package com.carepoint.common.error;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fields;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.fields = fields;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
