package ru.practicum.comment.exception;

import lombok.Getter;
import ru.practicum.common.Entities;

@Getter
public class NotFoundException extends RuntimeException {
    private final long id;

    public NotFoundException(Entities entity, long id) {
        super(String.format("%s with id=%d not exists", entity, id));
        this.id = id;
    }
}
