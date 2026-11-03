package ru.practicum.feign.exception;

public class EventServiceInteractionException extends RuntimeException {
    public EventServiceInteractionException(String message) {
        super(message);
    }
}
