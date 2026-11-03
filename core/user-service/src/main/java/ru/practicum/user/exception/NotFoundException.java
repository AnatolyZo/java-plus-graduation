package ru.practicum.user.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {
    private final long id;

    public NotFoundException(long id) {
        super(String.format("User with id=%d not exists", id));
        this.id = id;
    }
}
