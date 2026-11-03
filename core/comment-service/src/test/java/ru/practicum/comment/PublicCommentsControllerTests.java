package ru.practicum.comment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.practicum.comment.controller.PublicCommentsController;
import ru.practicum.comment.dto.CommentShortDto;
import ru.practicum.comment.service.CommentService;

@ExtendWith(MockitoExtension.class)
public class PublicCommentsControllerTests {
    private static final Long TEST_EVENT_ID = 1L;
    private static final int DEFAULT_FROM = 0;
    private static final int DEFAULT_SIZE = 10;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private PublicCommentsController controller;

    private CommentShortDto responseComment;
    private List<CommentShortDto> responseList;

    @BeforeEach
    void setUp() {
        responseComment = CommentShortDto.builder()
                .id(5L)
                .eventId(TEST_EVENT_ID)
                .text("Public comment text")
                .build();

        responseList = List.of(responseComment);
    }

    @Test
    void getComments_defaultParams_returnsOkAndList() {
        when(commentService.getComments(eq(TEST_EVENT_ID), eq(DEFAULT_FROM), eq(DEFAULT_SIZE)))
                .thenReturn(responseList);

        ResponseEntity<List<CommentShortDto>> result = controller.getComments(TEST_EVENT_ID, DEFAULT_FROM, DEFAULT_SIZE);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(5L);
    }

    @Test
    void getComments_customPagination_returnsOkAndList() {
        int from = 5;
        int size = 20;

        when(commentService.getComments(eq(TEST_EVENT_ID), eq(from), eq(size)))
                .thenReturn(responseList);

        ResponseEntity<List<CommentShortDto>> result = controller.getComments(TEST_EVENT_ID, from, size);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getComments_emptyResult_returnsOkAndEmptyList() {
        when(commentService.getComments(eq(TEST_EVENT_ID), anyInt(), anyInt()))
                .thenReturn(List.of());

        ResponseEntity<List<CommentShortDto>> result = controller.getComments(TEST_EVENT_ID, 0, 10);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).isEmpty();
    }
}
