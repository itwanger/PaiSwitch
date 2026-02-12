package com.paicoding.paiswitch.common.response;

import lombok.Getter;

@Getter
public enum ResponseCode {
    SUCCESS(200, "Success"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    CONFLICT(409, "Conflict"),
    VALIDATION_ERROR(422, "Validation Error"),
    INTERNAL_ERROR(500, "Internal Server Error"),

    USER_NOT_FOUND(1001, "User not found"),
    USER_ALREADY_EXISTS(1002, "User already exists"),
    INVALID_CREDENTIALS(1003, "Invalid username or password"),
    INVALID_TOKEN(1004, "Invalid or expired token"),

    PROVIDER_NOT_FOUND(2001, "Provider not found"),
    PROVIDER_ALREADY_EXISTS(2002, "Provider already exists"),
    PROVIDER_INACTIVE(2003, "Provider is inactive"),

    API_KEY_NOT_FOUND(3001, "API key not found"),
    API_KEY_INVALID(3002, "API key is invalid"),
    API_KEY_ENCRYPTION_ERROR(3003, "Failed to encrypt/decrypt API key"),

    CONFIG_NOT_FOUND(4001, "Configuration not found"),
    BACKUP_NOT_FOUND(4002, "Backup not found"),

    SWITCH_FAILED(5001, "Failed to switch provider"),
    AI_SERVICE_ERROR(5002, "AI service error");

    private final Integer code;
    private final String message;

    ResponseCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
