package com.eximbills.commandhandler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.LOCKED)
public class ResourceLockedException extends RuntimeException {

    public ResourceLockedException() {
        this("Resource locked!", null);
    }

    public ResourceLockedException(String message) {
        this(message, null);
    }

    public ResourceLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
