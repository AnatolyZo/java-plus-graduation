package ru.practicum.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.event.dto.event.UserEventUpdateAction;
import ru.practicum.event.dto.event.UserUpdateEventDto;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class UserUpdateEventDtoJsonTest {

    @Autowired
    private JacksonTester<UserUpdateEventDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserialize_absentFieldsShouldStayNull() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Новое название"
        ));

        var dto = objectMapper.readValue(body, UserUpdateEventDto.class);

        assertThat(dto.getTitle()).isEqualTo("Новое название");
        assertThat(dto.getPaid()).isNull();
        assertThat(dto.getParticipantLimit()).isNull();
        assertThat(dto.getStatus()).isNull();
    }

    @Test
    void deserialize_shouldMapStateActionAlias() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "stateAction", UserEventUpdateAction.SEND_TO_REVIEW.name()
        ));

        var dto = objectMapper.readValue(body, UserUpdateEventDto.class);

        assertThat(dto.getStatus()).isEqualTo(UserEventUpdateAction.SEND_TO_REVIEW);
    }
}