package ru.practicum.comment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.comment.dto.CommentShortDto;
import ru.practicum.comment.dto.CommentStatus;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.entity.Comment;
import ru.practicum.comment.exception.NotFoundException;
import ru.practicum.comment.repository.CommentRepository;
import ru.practicum.comment.service.CommentService;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
import ru.practicum.feign.result.RemoteCallResult;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CommentServiceCreateCommentIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @MockitoBean
    private EventExistsClientAdapter eventExistsClientAdapter;

    private static final long USER_ID = 1L;
    private static final long EVENT_ID = 10L;

    private NewCommentDto body;

    @BeforeEach
    void setUp() {
        body = NewCommentDto.builder().text("Новый комментарий").build();
    }

    @AfterEach
    void resetCircuitBreakers() {
        registry.circuitBreaker("user-checker").reset();
        registry.circuitBreaker("event-checker").reset();
        commentRepository.deleteAll();
    }

    @Test
    void createComment_successSavesCommentAndLeavesBothCircuitsClosed() {
        when(userExistsClientAdapter.checkExists(List.of(USER_ID)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(USER_ID)));
        when(eventExistsClientAdapter.checkExists(List.of(EVENT_ID)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(EVENT_ID)));

        CommentShortDto result = commentService.createComment(USER_ID, EVENT_ID, body);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getEventId()).isEqualTo(EVENT_ID);
        assertThat(result.getAuthorId()).isEqualTo(USER_ID);
        assertThat(result.getText()).isNull();

        Comment saved = commentRepository.findById(result.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CommentStatus.PENDING);
        assertThat(saved.getCreated()).isNotNull();
        assertThat(saved.getTextOnModeration()).isEqualTo("Новый комментарий");
        assertThat(saved.getText()).isNull();

        CircuitBreaker userCb = registry.circuitBreaker("user-checker");
        CircuitBreaker eventCb = registry.circuitBreaker("event-checker");
        assertThat(userCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(eventCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(userCb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(eventCb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void createComment_unknownUserThrowsNotFoundWithoutEventCheck() {
        when(userExistsClientAdapter.checkExists(List.of(USER_ID)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(99L)));

        assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                .isInstanceOf(NotFoundException.class);

        verify(eventExistsClientAdapter, never()).checkExists(anyList());
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void createComment_unknownEventThrowsNotFoundWithoutSaving() {
        when(userExistsClientAdapter.checkExists(List.of(USER_ID)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(USER_ID)));
        when(eventExistsClientAdapter.checkExists(List.of(EVENT_ID)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(55L)));

        assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                .isInstanceOf(NotFoundException.class);

        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void createComment_userCheckerBusinessFailurePropagatesAndKeepsCircuitsClosed() {
        when(userExistsClientAdapter.checkExists(anyList()))
                .thenReturn(new RemoteCallResult.BusinessFailure<>(404, "user not found"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                    .isInstanceOf(RemoteCallBusinessException.class)
                    .hasMessageContaining("user not found");
        }

        assertThat(registry.circuitBreaker("user-checker").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(registry.circuitBreaker("event-checker").getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        verify(userExistsClientAdapter, times(5)).checkExists(anyList());
        verify(eventExistsClientAdapter, never()).checkExists(anyList());
    }

    @Test
    void createComment_eventCheckerBusinessFailurePropagatesAndKeepsUserCircuitHealthy() {
        when(userExistsClientAdapter.checkExists(anyList()))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(USER_ID)));
        when(eventExistsClientAdapter.checkExists(anyList()))
                .thenReturn(new RemoteCallResult.BusinessFailure<>(404, "event not found"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                    .isInstanceOf(RemoteCallBusinessException.class)
                    .hasMessageContaining("event not found");
        }

        assertThat(registry.circuitBreaker("event-checker").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        CircuitBreaker userCb = registry.circuitBreaker("user-checker");
        assertThat(userCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(userCb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(5);

        verify(eventExistsClientAdapter, times(5)).checkExists(anyList());
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void userCheckerCircuitOpensOnTechnicalFailuresAndBlocksSixthCall() {
        when(userExistsClientAdapter.checkExists(anyList()))
                .thenReturn(new RemoteCallResult.TechnicalFailure<>("connection refused"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                    .isInstanceOf(RemoteCallTechnicalException.class)
                    .hasMessage("Сервис пользователей временно недоступен");
        }

        CircuitBreaker userCb = registry.circuitBreaker("user-checker");
        assertThat(userCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(userCb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                .isInstanceOf(CallNotPermittedException.class);

        verify(userExistsClientAdapter, times(5)).checkExists(anyList());
        verify(eventExistsClientAdapter, never()).checkExists(anyList());
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void eventCheckerCircuitOpensOnTechnicalFailuresAndBlocksSixthCall() {
        when(userExistsClientAdapter.checkExists(anyList()))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(USER_ID)));
        when(eventExistsClientAdapter.checkExists(anyList()))
                .thenReturn(new RemoteCallResult.TechnicalFailure<>("connection refused"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                    .isInstanceOf(RemoteCallTechnicalException.class)
                    .hasMessage("Сервис событий временно недоступен");
        }

        CircuitBreaker eventCb = registry.circuitBreaker("event-checker");
        assertThat(eventCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(eventCb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        assertThatThrownBy(() -> commentService.createComment(USER_ID, EVENT_ID, body))
                .isInstanceOf(CallNotPermittedException.class);

        verify(eventExistsClientAdapter, times(5)).checkExists(anyList());
        verify(userExistsClientAdapter, times(6)).checkExists(anyList());
        assertThat(registry.circuitBreaker("user-checker").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        assertThat(commentRepository.count()).isZero();
    }
}