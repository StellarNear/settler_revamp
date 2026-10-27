package com.apogames.tiwanaku.server;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiException extends ResponseStatusException {
    public ApiException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static ApiException badRequest(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, reason);
    }

    public static ApiException notFound(String reason) {
        return new ApiException(HttpStatus.NOT_FOUND, reason);
    }

    public static ApiException conflict(String reason) {
        return new ApiException(HttpStatus.CONFLICT, reason);
    }
}
