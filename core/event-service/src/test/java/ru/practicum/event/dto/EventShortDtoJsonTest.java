package ru.practicum.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.event.dto.category.CategoryDto;
import ru.practicum.event.dto.event.EventShortDto;

import java.time.LocalDateTime;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class EventShortDtoJsonTest {

    @Autowired
    private JacksonTester<EventShortDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serialize_shouldUseDatePatternAndNestedCategory() throws Exception {
        var dto = EventShortDto.builder()
                .id(1L)
                .annotation("Достаточно длинная аннотация события")
                .category(CategoryDto.builder().id(3L).name("Концерты").build())
                .confirmedRequests(5)
                .eventDate(LocalDateTime.of(2027, 6, 1, 12, 0, 0))
                .initiator(7L)
                .paid(false)
                .title("Тестовое событие")
                .rating(4.5)
                .build();

        String expected = objectMapper.writeValueAsString(Map.ofEntries(
                entry("id", 1L),
                entry("annotation", "Достаточно длинная аннотация события"),
                entry("category", CategoryDto.builder().id(3L).name("Концерты").build()),
                entry("confirmedRequests", 5),
                entry("eventDate", "2027-06-01 12:00:00"),
                entry("initiator", 7L),
                entry("paid", false),
                entry("title", "Тестовое событие"),
                entry("rating", 4.5)
        ));

        assertThat(json.write(dto)).isEqualToJson(expected);
    }
}