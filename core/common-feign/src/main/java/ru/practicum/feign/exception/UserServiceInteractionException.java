package ru.practicum.feign.exception;

public class UserServiceInteractionException extends RuntimeException {
    public UserServiceInteractionException(String message) {
        super(message);
    }
}
