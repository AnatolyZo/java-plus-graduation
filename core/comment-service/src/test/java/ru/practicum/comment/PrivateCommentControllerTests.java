package ru.practicum.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.practicum.comment.controller.PrivateCommentController;
import ru.practicum.comment.dto.CommentShortDto;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.dto.UpdateCommentDto;
import ru.practicum.comment.service.CommentService;

@ExtendWith(MockitoExtension.class)
public class PrivateCommentControllerTests {
    private static final long TEST_USER_ID = 1L;
    private static final long TEST_EVENT_ID = 2L;
    private static final long TEST_COMMENT_ID = 3L;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private PrivateCommentController controller;

    private NewCommentDto createBody;
    private UpdateCommentDto updateBody;
    private CommentShortDto responseComment;
    private List<CommentShortDto> responseList;

    @BeforeEach
    void setUp() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 10, 14, 0, 0);

        createBody = NewCommentDto.builder()
                .text("New comment text")
                .build();

        updateBody = UpdateCommentDto.builder()
                .text("Updated comment text")
                .build();

        responseComment = CommentShortDto.builder()
                .id(TEST_COMMENT_ID)
                .eventId(TEST_EVENT_ID)
                .text("Sample comment text")
                .build();

        responseList = List.of(responseComment);
    }

    @Test
    void getComments_returnsOkAndList() {
        when(commentService.getUsersComments(eq(TEST_USER_ID)))
                .thenReturn(responseList);

        ResponseEntity<List<CommentShortDto>> result = controller.getComments(TEST_USER_ID);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(TEST_COMMENT_ID);
    }

    @Test
    void createComment_returnsCreated() {
        when(commentService.createComment(eq(TEST_USER_ID), eq(TEST_EVENT_ID), any(NewCommentDto.class)))
                .thenReturn(responseComment);

        ResponseEntity<CommentShortDto> result = controller.createComment(TEST_USER_ID, TEST_EVENT_ID, createBody);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_COMMENT_ID);
        Assertions.assertThat(result.getBody().getText()).isEqualTo("Sample comment text");
    }

    @Test
    void updateComment_returnsOk() {
        when(commentService.updateComment(eq(TEST_USER_ID), eq(TEST_COMMENT_ID), any(UpdateCommentDto.class)))
                .thenReturn(responseComment);

        ResponseEntity<CommentShortDto> result = controller.updateComment(TEST_USER_ID, TEST_COMMENT_ID, updateBody);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_COMMENT_ID);
        Assertions.assertThat(result.getBody().getText()).isEqualTo("Sample comment text");
    }

    @Test
    void deleteComment_returnsNoContent() {
        ResponseEntity<Void> result = controller.deleteComment(TEST_USER_ID, TEST_COMMENT_ID);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Assertions.assertThat(result.getBody()).isNull();
    }
}
