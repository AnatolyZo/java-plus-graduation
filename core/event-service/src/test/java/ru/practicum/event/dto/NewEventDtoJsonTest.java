package ru.practicum.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.event.dto.event.NewEventDto;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class NewEventDtoJsonTest {

    @Autowired
    private JacksonTester<NewEventDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserialize_shouldMapAllFields() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "annotation", "Достаточно длинная аннотация события",
                "category", 3L,
                "description", "Достаточно длинное описание события для валидации",
                "eventDate", "2027-06-01 12:00:00",
                "location", Map.of("lat", 55.75, "lon", 37.61),
                "paid", true,
                "participantLimit", 10,
                "requestModeration", false,
                "title", "Тестовое событие"
        ));

        var dto = objectMapper.readValue(body, NewEventDto.class);

        assertThat(dto.getAnnotation()).isEqualTo("Достаточно длинная аннотация события");
        assertThat(dto.getCategory()).isEqualTo(3L);
        assertThat(dto.getEventDate()).isEqualTo(LocalDateTime.of(2027, 6, 1, 12, 0, 0));
        assertThat(dto.isPaid()).isTrue();
        assertThat(dto.getParticipantLimit()).isEqualTo(10);
        assertThat(dto.getRequestModeration()).isFalse();
        assertThat(dto.getRequestModerationOrDefault()).isFalse();
        assertThat(dto.getTitle()).isEqualTo("Тестовое событие");
    }

    @Test
    void deserialize_absentOptionalFieldsShouldGetDefaults() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "annotation", "Достаточно длинная аннотация события",
                "category", 3L,
                "description", "Достаточно длинное описание события для валидации",
                "eventDate", "2027-06-01 12:00:00",
                "location", Map.of("lat", 55.75, "lon", 37.61),
                "title", "Тестовое событие"
        ));

        var dto = objectMapper.readValue(body, NewEventDto.class);

        assertThat(dto.isPaid()).isFalse();
        assertThat(dto.getParticipantLimit()).isZero();
        assertThat(dto.getRequestModeration()).isNull();
        assertThat(dto.getRequestModerationOrDefault()).isTrue();
    }
}