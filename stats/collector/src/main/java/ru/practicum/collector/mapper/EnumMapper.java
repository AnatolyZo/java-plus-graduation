package ru.practicum.collector.mapper;

import java.util.Arrays;
import java.util.Optional;

public class EnumMapper {
    public static <T extends Enum<T>> Optional<T> toAppEnum(T[] enumValues, String protoName) {
        if (protoName == null) {
            return Optional.empty();
        }

        String formattedName = protoName.substring(protoName.indexOf("_") + 1);

        return Arrays.stream(enumValues)
                .filter(type -> type.name().equalsIgnoreCase(formattedName))
                .findFirst();
    }
}
