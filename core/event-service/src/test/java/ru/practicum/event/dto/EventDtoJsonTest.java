package ru.practicum.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.event.dto.category.CategoryDto;
import ru.practicum.event.dto.event.EventDto;
import ru.practicum.event.dto.event.EventStatus;
import ru.practicum.event.dto.event.Location;

import java.time.LocalDateTime;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class EventDtoJsonTest {

    @Autowired
    private JacksonTester<EventDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    private Location location() {
        return new Location(55.75f, 37.61f);
    }

    private EventDto publishedEvent() {
        return EventDto.builder()
                .id(1L)
                .annotation("Достаточно длинная аннотация события")
                .category(CategoryDto.builder().id(3L).name("Концерты").build())
                .confirmedRequests(5)
                .createdOn(LocalDateTime.of(2026, 1, 10, 10, 0, 0))
                .description("Достаточно длинное описание события для валидации")
                .eventDate(LocalDateTime.of(2027, 6, 1, 12, 0, 0))
                .initiator(7L)
                .location(location())
                .paid(true)
                .participantLimit(100)
                .publishedOn(LocalDateTime.of(2026, 2, 1, 9, 30, 0))
                .requestModeration(true)
                .status(EventStatus.PUBLISHED)
                .title("Тестовое событие")
                .rating(4.5)
                .build();
    }

    @Test
    void serialize_shouldWriteAllFieldsWithFormats() throws Exception {
        String expected = objectMapper.writeValueAsString(Map.ofEntries(
                entry("id", 1L),
                entry("annotation", "Достаточно длинная аннотация события"),
                entry("category", CategoryDto.builder().id(3L).name("Концерты").build()),
                entry("confirmedRequests", 5),
                entry("createdOn", "2026-01-10 10:00:00"),
                entry("description", "Достаточно длинное описание события для валидации"),
                entry("eventDate", "2027-06-01 12:00:00"),
                entry("initiator", 7L),
                entry("location", location()),
                entry("paid", true),
                entry("participantLimit", 100),
                entry("publishedOn", "2026-02-01 09:30:00"),
                entry("requestModeration", true),
                entry("state", EventStatus.PUBLISHED.name()),
                entry("title", "Тестовое событие"),
                entry("rating", 4.5)
        ));

        assertThat(json.write(publishedEvent())).isEqualToJson(expected);
    }

    @Test
    void serialize_shouldUseStateAliasNotStatus() throws Exception {
        assertThat(json.write(publishedEvent())).doesNotHaveJsonPathValue("$.status");
        assertThat(json.write(publishedEvent()))
                .extractingJsonPathStringValue("$.state")
                .isEqualTo(EventStatus.PUBLISHED.name());
    }

    @Test
    void serialize_pendingEventShouldContainNullPublishedOn() throws Exception {
        // publishedOn — единственное nullable поле: у неопубликованного события null
        // должен присутствовать в JSON как null (если позже добавят @JsonInclude,
        // тест сразу это заметит)
        var dto = EventDto.builder()
                .id(2L)
                .annotation("Достаточно длинная аннотация события")
                .category(CategoryDto.builder().id(3L).name("Концерты").build())
                .confirmedRequests(5)
                .createdOn(LocalDateTime.of(2026, 1, 10, 10, 0, 0))
                .description("Достаточно длинное описание события для валидации")
                .eventDate(LocalDateTime.of(2027, 6, 1, 12, 0, 0))
                .initiator(7L)
                .location(new Location(55.75f, 37.61f)) // подстрой под свой конструктор Location
                .paid(true)
                .participantLimit(100)
                .requestModeration(true)
                .status(EventStatus.PENDING)
                .rating(4.5)
                .build();

        assertThat(json.write(dto))
                .extractingJsonPathValue("$.publishedOn")
                .isNull();
    }
}