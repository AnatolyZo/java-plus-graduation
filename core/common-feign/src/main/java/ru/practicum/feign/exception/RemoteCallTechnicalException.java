package ru.practicum.feign.exception;

public class RemoteCallTechnicalException extends RuntimeException {
    public RemoteCallTechnicalException(String message) {
        super(message);
    }
}
