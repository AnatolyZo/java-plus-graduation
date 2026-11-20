package ru.practicum.event;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.event.exception.EventNotMetException;
import ru.practicum.event.feign.RequestExistsClientAdapter;
import ru.practicum.event.service.EventService;
import ru.practicum.event.service.support.StatsInteractionService;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.result.RemoteCallResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class EventServiceAddLikeIntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private RequestExistsClientAdapter requestExistsClientAdapter;

    @MockitoBean
    private StatsInteractionService statsInteractionService;

    @AfterEach
    void resetCircuitBreaker() {
        registry.circuitBreaker("request-checker").reset();
    }

    @Test
    void addLike_successCallsStatsAndLeavesCircuitClosed() {
        long userId = 1L;
        long eventId = 10L;

        when(requestExistsClientAdapter.checkExistenceByEventIdAndRequesterId(eventId, userId))
                .thenReturn(new RemoteCallResult.Success<>(true));

        eventService.addLike(userId, eventId);

        verify(statsInteractionService).addLike(userId, eventId);

        CircuitBreaker cb = registry.circuitBreaker("request-checker");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void addLike_requestNotMetThrowsEventNotMetWithoutStatsCall() {
        when(requestExistsClientAdapter.checkExistenceByEventIdAndRequesterId(anyLong(), anyLong()))
                .thenReturn(new RemoteCallResult.Success<>(false));

        assertThatThrownBy(() -> eventService.addLike(1L, 10L))
                .isInstanceOf(EventNotMetException.class);

        verify(statsInteractionService, never()).addLike(anyLong(), anyLong());
    }

    @Test
    void addLike_businessFailureThrowsAndDoesNotOpenCircuit() {
        long userId = 1L;
        long eventId = 10L;

        when(requestExistsClientAdapter.checkExistenceByEventIdAndRequesterId(anyLong(), anyLong()))
                .thenReturn(new RemoteCallResult.BusinessFailure<>(404, "request not found"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> eventService.addLike(userId, eventId))
                    .isInstanceOf(RemoteCallBusinessException.class);
        }

        CircuitBreaker cb = registry.circuitBreaker("request-checker");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        verify(requestExistsClientAdapter, times(5))
                .checkExistenceByEventIdAndRequesterId(anyLong(), anyLong());
    }

    @Test
    void circuitOpensAfterWindowOfTechnicalFailuresAndFallbackReturnsFalse() {
        long userId = 1L;
        long eventId = 10L;

        when(requestExistsClientAdapter.checkExistenceByEventIdAndRequesterId(anyLong(), anyLong()))
                .thenReturn(new RemoteCallResult.TechnicalFailure<>("connection refused"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> eventService.addLike(userId, eventId))
                    .isInstanceOf(EventNotMetException.class);
        }

        CircuitBreaker cb = registry.circuitBreaker("request-checker");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        assertThatThrownBy(() -> eventService.addLike(userId, eventId))
                .isInstanceOf(EventNotMetException.class);

        verify(requestExistsClientAdapter, times(5))
                .checkExistenceByEventIdAndRequesterId(anyLong(), anyLong());
    }
}