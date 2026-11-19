package ru.practicum.request.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class RequestDtoJsonTest {

    @Autowired
    private JacksonTester<RequestDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serialize_shouldUseDatePatternAndStatusName() throws Exception {
        var dto = RequestDto.builder()
                .id(1L)
                .created(LocalDateTime.of(2026, 11, 19, 10, 30, 0))
                .event(10L)
                .requester(20L)
                .status(RequestStatus.PENDING)
                .build();

        String expected = objectMapper.writeValueAsString(Map.of(
                "id", 1L,
                "created", "2026-11-19 10:30:00",
                "event", 10L,
                "requester", 20L,
                "status", RequestStatus.PENDING.name()
        ));

        assertThat(json.write(dto)).isEqualToJson(expected);
    }

    @Test
    void serialize_datePatternShouldPreserveLeadingZeroes() throws Exception {
        var dto = RequestDto.builder()
                .id(1L)
                .created(LocalDateTime.of(2026, 1, 2, 9, 5, 7))
                .event(10L)
                .requester(20L)
                .status(RequestStatus.CONFIRMED)
                .build();

        assertThat(json.write(dto))
                .extractingJsonPathStringValue("$.created")
                .isEqualTo("2026-01-02 09:05:07");
    }
}