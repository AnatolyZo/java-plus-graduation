package ru.practicum.compilation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class NewCompilationDtoJsonTest {

    @Autowired
    private JacksonTester<NewCompilationDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserialize_shouldMapAllFields() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Топ недели",
                "pinned", true,
                "events", List.of(3L, 7L)
        ));

        var dto = objectMapper.readValue(body, NewCompilationDto.class);

        assertThat(dto.getTitle()).isEqualTo("Топ недели");
        assertThat(dto.isPinned()).isTrue();
        assertThat(dto.getEvents()).containsExactly(3L, 7L);
    }

    @Test
    void deserialize_absentEventsShouldStayNull() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Без событий"
        ));

        var dto = objectMapper.readValue(body, NewCompilationDto.class);

        assertThat(dto.getEvents()).isNull();
        assertThat(dto.isPinned()).isFalse();
    }

    @Test
    void serialize_shouldWritePlainFieldNames() throws Exception {
        var dto = NewCompilationDto.builder()
                .title("Топ недели")
                .pinned(false)
                .events(List.of(1L, 2L))
                .build();

        String expected = objectMapper.writeValueAsString(Map.of(
                "events", List.of(1L, 2L),
                "pinned", false,
                "title", "Топ недели"
        ));

        assertThat(json.write(dto)).isEqualToJson(expected);
    }
}