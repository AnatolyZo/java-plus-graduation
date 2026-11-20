package ru.practicum.compilation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.compilation.feign.EventShortDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class CompilationDtoJsonTest {

    @Autowired
    private JacksonTester<CompilationDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    private EventShortDto event(int id, String title) {
        return EventShortDto.builder()
                .id(id)
                .title(title)
                .eventDate(LocalDateTime.of(2024, 6, 1, 18, 0, 0))
                .build();
    }

    @Test
    void serialize_shouldIncludeNestedEventsList() throws Exception {
        var dto = CompilationDto.builder()
                .id(1L)
                .pinned(true)
                .title("Летняя подборка")
                .events(List.of(event(10, "Концерт"), event(20, "Лекция")))
                .build();

        String expected = objectMapper.writeValueAsString(Map.of(
                "id", 1L,
                "pinned", true,
                "title", "Летняя подборка",
                "events", List.of(
                        event(10, "Концерт"),
                        event(20, "Лекция")
                )
        ));

        assertThat(json.write(dto)).isEqualToJson(expected);
    }

    @Test
    void serialize_emptyEventsShouldBeEmptyArrayNotNull() throws Exception {
        var dto = CompilationDto.builder()
                .id(2L)
                .pinned(false)
                .title("Пустая подборка")
                .build();

        assertThat(json.write(dto))
                .extractingJsonPathArrayValue("$.events")
                .isEmpty();
    }
}