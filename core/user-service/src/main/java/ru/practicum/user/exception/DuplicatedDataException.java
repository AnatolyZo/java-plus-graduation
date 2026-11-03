package ru.practicum.user.exception;

import lombok.Getter;

@Getter
public class DuplicatedDataException extends RuntimeException {
    private final String fieldName;
    private final Object value;

    public DuplicatedDataException(String fieldName, Object value) {
        super(String.format("User with parameter '%s'=%s already exists", fieldName, value.toString()));
        this.fieldName = fieldName;
        this.value = value;
    }
}

