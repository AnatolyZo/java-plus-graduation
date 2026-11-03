package ru.practicum.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.practicum.comment.controller.AdminCommentController;
import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.CommentStatus;
import ru.practicum.comment.dto.ModerationAction;
import ru.practicum.comment.service.CommentService;

@ExtendWith(MockitoExtension.class)
public class AdminCommentControllerTests {
    private static final long TEST_ADMIN_ID = 1L;
    private static final long TEST_COMMENT_ID = 5L;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private AdminCommentController controller;

    private CommentDto responseComment;
    private List<CommentDto> responseList;

    @BeforeEach
    void setUp() {
        CommentStatus status = CommentStatus.APPROVED;

        responseComment = CommentDto.builder()
                .id(TEST_COMMENT_ID)
                .text("Approved comment text")
                .authorId(10L)
                .eventId(20L)
                .status(status)
                .build();

        responseList = List.of(responseComment);
    }

    @Test
    void searchComments_defaultParams_returnsOkAndList() {
        when(commentService.searchComments(
                eq(TEST_ADMIN_ID),
                isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(responseList);

        ResponseEntity<List<CommentDto>> result = controller.searchComments(
                TEST_ADMIN_ID, null, null, null, null, null, null, 0, 10);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(TEST_COMMENT_ID);
    }

    @Test
    void searchComments_withFiltersAndPagination_returnsOkAndFilteredList() {
        String text = "search text";
        List<Long> authorsIds = List.of(1L, 2L);
        String rangeStart = "2024-01-01 00:00:00";
        String rangeEnd = "2024-12-31 23:59:59";
        List<Long> eventIds = List.of(3L, 4L);
        List<CommentStatus> states = List.of(CommentStatus.APPROVED, CommentStatus.REJECTED);
        int from = 5;
        int size = 10;

        when(commentService.searchComments(
                eq(TEST_ADMIN_ID), eq(text), eq(authorsIds), eq(rangeStart), eq(rangeEnd),
                eq(eventIds), eq(states), eq(from), eq(size)))
                .thenReturn(responseList);

        ResponseEntity<List<CommentDto>> result = controller.searchComments(
                TEST_ADMIN_ID, text, authorsIds, rangeStart, rangeEnd, eventIds, states, from, size);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void searchComments_emptyResult_returnsEmptyList() {
        when(commentService.searchComments(
                eq(TEST_ADMIN_ID),
                isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        ResponseEntity<List<CommentDto>> result = controller.searchComments(
                TEST_ADMIN_ID, null, null, null, null, null, null, 0, 10);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).isEmpty();
    }

    @Test
    void moderateComment_returnsOk() {
        ModerationAction action = ModerationAction.APPROVE;

        when(commentService.moderateComment(eq(TEST_ADMIN_ID), eq(TEST_COMMENT_ID), eq(action)))
                .thenReturn(responseComment);

        ResponseEntity<CommentDto> result = controller.moderateComment(TEST_ADMIN_ID, TEST_COMMENT_ID, action);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_COMMENT_ID);
        Assertions.assertThat(result.getBody().getText()).isEqualTo("Approved comment text");
    }

    @Test
    void deleteComment_returnsNoContent() {
        commentService.deleteCommentByAdmin(eq(TEST_ADMIN_ID), eq(TEST_COMMENT_ID));

        ResponseEntity<Void> result = controller.deleteComment(TEST_ADMIN_ID, TEST_COMMENT_ID);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Assertions.assertThat(result.getBody()).isNull();
    }
}
