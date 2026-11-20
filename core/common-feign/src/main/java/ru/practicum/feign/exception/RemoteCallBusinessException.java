package ru.practicum.feign.exception;

import lombok.Getter;

public class RemoteCallBusinessException extends RuntimeException {
    @Getter
    private final int status;

    public RemoteCallBusinessException(int status, String message) {
        super(message);
        this.status = status;
    }
}
