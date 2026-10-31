package ru.practicum.event.exception;

public class WrongDateIntervalException extends RuntimeException {
    public WrongDateIntervalException(String message) {
        super(message);
    }
}
