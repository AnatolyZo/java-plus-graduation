package ru.practicum.comment.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class CommentShortDtoJsonTest {

    @Autowired
    private JacksonTester<CommentShortDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    private String expectedJson(
            long id, String text, String created, String updated,
            long eventId, long authorId
    ) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id", id,
                "text", text,
                "created", created,
                "updated", updated,
                "eventId", eventId,
                "authorId", authorId
        ));
    }

    @Test
    void serialize_shouldUseDatePatternWithoutT() throws Exception {
        var dto = CommentShortDto.builder()
                .id(5L)
                .text("Короткий комментарий")
                .created(LocalDateTime.of(2024, 3, 10, 9, 5, 7))
                .updated(LocalDateTime.of(2024, 3, 11, 10, 20, 30))
                .eventId(10L)
                .authorId(20L)
                .build();

        String expected = expectedJson(
                5L, "Короткий комментарий",
                "2024-03-10 09:05:07", "2024-03-11 10:20:30",
                10L, 20L
        );

        assertThat(json.write(dto)).isEqualToJson(expected);
    }

    @Test
    void serialize_leadingZeroesShouldBePreserved() throws Exception {
        var dto = CommentShortDto.builder()
                .id(1L)
                .text("t")
                .created(LocalDateTime.of(2024, 1, 2, 3, 4, 5))
                .updated(LocalDateTime.of(2024, 1, 2, 3, 4, 5))
                .eventId(1L)
                .authorId(1L)
                .build();

        assertThat(json.write(dto))
                .extractingJsonPathStringValue("$.created")
                .isEqualTo("2024-01-02 03:04:05");
    }
}