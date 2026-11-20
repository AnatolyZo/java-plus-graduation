package ru.practicum.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.event.dto.event.AdminEventUpdateAction;
import ru.practicum.event.dto.event.AdminUpdateEventDto;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class AdminUpdateEventDtoJsonTest {

    @Autowired
    private JacksonTester<AdminUpdateEventDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserialize_absentFieldsShouldStayNull() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Новое название"
        ));

        var dto = objectMapper.readValue(body, AdminUpdateEventDto.class);

        assertThat(dto.getTitle()).isEqualTo("Новое название");
        assertThat(dto.getAnnotation()).isNull();
        assertThat(dto.getCategory()).isNull();
        assertThat(dto.getDescription()).isNull();
        assertThat(dto.getEventDate()).isNull();
        assertThat(dto.getLocation()).isNull();
        assertThat(dto.getPaid()).isNull();
        assertThat(dto.getParticipantLimit()).isNull();
        assertThat(dto.getRequestModeration()).isNull();
        assertThat(dto.getStatus()).isNull();
    }

    @Test
    void deserialize_shouldMapStateActionAlias() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "stateAction", AdminEventUpdateAction.PUBLISH_EVENT.name()
        ));

        var dto = objectMapper.readValue(body, AdminUpdateEventDto.class);

        assertThat(dto.getStatus()).isEqualTo(AdminEventUpdateAction.PUBLISH_EVENT);
        assertThat(dto.getPaid()).isNull();
    }

    @Test
    void deserialize_eventDateShouldParseCustomPattern() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "eventDate", "2027-06-01 12:00:00"
        ));

        var dto = objectMapper.readValue(body, AdminUpdateEventDto.class);

        assertThat(dto.getEventDate()).isEqualTo(LocalDateTime.of(2027, 6, 1, 12, 0, 0));
    }

    @Test
    void serialize_shouldWriteStateActionAlias() throws Exception {
        var dto = AdminUpdateEventDto.builder()
                .status(AdminEventUpdateAction.REJECT_EVENT)
                .build();

        assertThat(json.write(dto)).doesNotHaveJsonPathValue("$.status");
        assertThat(json.write(dto))
                .extractingJsonPathStringValue("$.stateAction")
                .isEqualTo(AdminEventUpdateAction.REJECT_EVENT.name());
    }
}