package ru.practicum.compilation.feign;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import ru.practicum.compilation.dto.CategoryDto;

import java.time.LocalDateTime;

@Builder
@Getter
@ToString
public class EventShortDto {
    private long id;
    private String annotation;
    private CategoryDto category;
    private int confirmedRequests;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;
    private long initiatorId;
    private boolean paid;
    private String title;
    private long views;
}
