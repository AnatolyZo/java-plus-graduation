package ru.practicum.event.exception;

public class EventNotMetException extends RuntimeException {
    public EventNotMetException(long eventId, long userId) {
        super(String.format("Мероприятие с id %d не посещено пользователем с id %d", eventId, userId));
    }
}
