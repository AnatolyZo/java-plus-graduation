package ru.practicum.request.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class UpdateRequestStatusDtoJsonTest {

    @Autowired
    private JacksonTester<UpdateRequestStatusDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserialize_shouldMapRequestIdsAndStatus() throws Exception {
        String body = objectMapper.writeValueAsString(Map.ofEntries(
                entry("requestIds", List.of(1L, 2L, 5L)),
                entry("status", RequestStatus.CONFIRMED.name())
        ));

        var dto = objectMapper.readValue(body, UpdateRequestStatusDto.class);

        assertThat(dto.getRequestIds()).containsExactly(1L, 2L, 5L);
        assertThat(dto.getStatus()).isEqualTo(RequestStatus.CONFIRMED);
    }

    @Test
    void deserialize_singleRequestIdShouldBecomeSingletonList() throws Exception {
        String body = objectMapper.writeValueAsString(Map.ofEntries(
                entry("requestIds", List.of(42L)),
                entry("status", RequestStatus.REJECTED.name())
        ));

        var dto = objectMapper.readValue(body, UpdateRequestStatusDto.class);

        assertThat(dto.getRequestIds()).containsExactly(42L);
        assertThat(dto.getStatus()).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    void serialize_shouldWriteFieldNamesAsDeclared() throws Exception {
        var dto = UpdateRequestStatusDto.builder()
                .requestIds(List.of(1L, 2L))
                .status(RequestStatus.PENDING)
                .build();

        String expected = objectMapper.writeValueAsString(Map.ofEntries(
                entry("requestIds", List.of(1L, 2L)),
                entry("status", RequestStatus.PENDING.name())
        ));

        assertThat(json.write(dto)).isEqualToJson(expected);
    }
}