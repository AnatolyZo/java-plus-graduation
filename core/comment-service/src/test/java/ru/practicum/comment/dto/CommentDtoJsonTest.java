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
class CommentDtoJsonTest {

    @Autowired
    private JacksonTester<CommentDto> json;

    @Autowired
    private ObjectMapper objectMapper;

    private String expectedJson(
            long id, String text, String created, String updated,
            long eventId, long authorId, String state,
            long moderatorId, String moderated, String moderationReason
    ) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id", id,
                "text", text,
                "created", created,
                "updated", updated,
                "eventId", eventId,
                "authorId", authorId,
                "state", state,
                "moderatorId", moderatorId,
                "moderated", moderated,
                "moderationReason", moderationReason
        ));
    }

    private CommentDto fullDto() {
        return CommentDto.builder()
                .id(1L)
                .text("Комментарий")
                .created(LocalDateTime.of(2024, 5, 1, 12, 0, 0))
                .updated(LocalDateTime.of(2024, 5, 2, 13, 30, 0))
                .eventId(10L)
                .authorId(20L)
                .status(CommentStatus.PENDING)
                .moderatorId(99L)
                .moderated(LocalDateTime.of(2024, 5, 2, 14, 0, 0))
                .moderationReason("ok")
                .build();
    }

    @Test
    void serialize_shouldUseStateAliasAndIsoDate() throws Exception {
        String expected = expectedJson(
                1L, "Комментарий", "2024-05-01T12:00:00", "2024-05-02T13:30:00",
                10L, 20L, CommentStatus.PENDING.name(),
                99L, "2024-05-02T14:00:00", "ok"
        );

        assertThat(json.write(fullDto())).isEqualToJson(expected);
    }

    @Test
    void serialize_shouldNotExposeStatusFieldName() throws Exception {
        // если @JsonProperty слетит, в JSON утечёт поле "status" вместо "state"
        assertThat(json.write(fullDto())).doesNotHaveJsonPathValue("$.status");
    }

    @Test
    void serialize_nullFieldsShouldBePresentAsNull() throws Exception {
        var dto = CommentDto.builder()
                .id(2L)
                .text("t")
                .created(LocalDateTime.of(2024, 1, 1, 0, 0, 0))
                .updated(LocalDateTime.of(2024, 1, 1, 0, 0, 0))
                .eventId(10L)
                .authorId(20L)
                .status(CommentStatus.PENDING)
                .build();

        assertThat(json.write(dto))
                .extractingJsonPathValue("$.moderatorId").isNull();
        assertThat(json.write(dto))
                .extractingJsonPathValue("$.moderated").isNull();
        assertThat(json.write(dto))
                .extractingJsonPathValue("$.moderationReason").isNull();
    }
}