package ru.practicum.request;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.explorewithme.CollectorClient;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
import ru.practicum.feign.result.RemoteCallResult;
import ru.practicum.request.dto.RequestStatus;
import ru.practicum.request.dto.UpdateRequestStatusDto;
import ru.practicum.request.entity.Request;
import ru.practicum.request.exception.UnavailableUpdateException;
import ru.practicum.request.feign.EventDto;
import ru.practicum.request.feign.EventFetchClientAdapter;
import ru.practicum.request.feign.EventStatus;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.service.RequestService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class RequestServiceCircuitBreakerIntegrationTest {

    @Autowired
    private RequestService requestService;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private EventFetchClientAdapter eventFetchClientAdapter;

    @MockitoBean
    private EventExistsClientAdapter eventExistsClientAdapter;

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @MockitoBean
    private CollectorClient collectorClient;

    private static final AtomicLong SEQ = new AtomicLong();

    private long userId;
    private long eventId;

    @BeforeEach
    void setUp() {
        userId = SEQ.incrementAndGet();
        eventId = SEQ.incrementAndGet();
        requestRepository.deleteAll();
    }

    @AfterEach
    void resetCircuitBreakers() {
        registry.circuitBreaker("event-checker").reset();
        registry.circuitBreaker("user-checker").reset();
        registry.circuitBreaker("event-fetch").reset();
        registry.circuitBreaker("event-user-fetch").reset();
        registry.circuitBreaker("event-update").reset();
        requestRepository.deleteAll();
    }

    private EventDto event(int confirmedRequests, int participantLimit, boolean requestModeration) {
        return new EventDto(eventId, confirmedRequests, userId, participantLimit, requestModeration, EventStatus.PUBLISHED);
    }

    private Request pendingRequest(long requesterId) {
        return requestRepository.save(Request.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(requesterId)
                .status(RequestStatus.PENDING)
                .build());
    }

    @Test
    void updateRequestStatuses_confirmWithModerationAndFreeSlotsConfirmsRequests() {
        Request first = pendingRequest(2L);
        Request second = pendingRequest(3L);

        when(eventFetchClientAdapter.getEventByIdAndUserId(userId, eventId))
                .thenReturn(new RemoteCallResult.Success<>(event(0, 5, true)));
        when(eventFetchClientAdapter.updateConfirmedRequests(eq(eventId), any()))
                .thenReturn(new RemoteCallResult.Success<>(event(1, 5, true)));

        requestService.updateRequestStatuses(userId, eventId,
                UpdateRequestStatusDto.builder()
                        .requestIds(List.of(first.getId(), second.getId()))
                        .status(RequestStatus.CONFIRMED)
                        .build());

        assertThat(requestRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.CONFIRMED);
        assertThat(requestRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.CONFIRMED);
        verify(eventFetchClientAdapter, atLeastOnce())
                .updateConfirmedRequests(eq(eventId), any());
        assertThat(registry.circuitBreaker("event-user-fetch").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void updateRequestStatuses_noModerationStillIncrementsConfirmedCounter() {
        Request first = pendingRequest(2L);

        when(eventFetchClientAdapter.getEventByIdAndUserId(userId, eventId))
                .thenReturn(new RemoteCallResult.Success<>(event(0, 0, true)));
        when(eventFetchClientAdapter.updateConfirmedRequests(eq(eventId), any()))
                .thenReturn(new RemoteCallResult.Success<>(event(1, 0, true)));

        requestService.updateRequestStatuses(userId, eventId,
                UpdateRequestStatusDto.builder()
                        .requestIds(List.of(first.getId()))
                        .status(RequestStatus.CONFIRMED)
                        .build());

        assertThat(requestRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.CONFIRMED);
        verify(eventFetchClientAdapter, atLeastOnce())
                .updateConfirmedRequests(eq(eventId), any());
    }

    @Test
    void updateRequestStatuses_rejectPathMarksRequestsRejected() {
        Request first = pendingRequest(2L);

        when(eventFetchClientAdapter.getEventByIdAndUserId(userId, eventId))
                .thenReturn(new RemoteCallResult.Success<>(event(0, 5, true)));

        requestService.updateRequestStatuses(userId, eventId,
                UpdateRequestStatusDto.builder()
                        .requestIds(List.of(first.getId()))
                        .status(RequestStatus.REJECTED) // было CONFIRMED — опечатка уводила в confirm-ветку
                        .build());

        assertThat(requestRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.REJECTED);
        verify(eventFetchClientAdapter, never()).updateConfirmedRequests(anyLong(), any());
    }

    @Test
    void updateRequestStatuses_noSlotsLeftThrowsUnavailableUpdate() {
        pendingRequest(2L);

        when(eventFetchClientAdapter.getEventByIdAndUserId(userId, eventId))
                .thenReturn(new RemoteCallResult.Success<>(event(2, 2, true)));

        assertThatThrownBy(() -> requestService.updateRequestStatuses(userId, eventId,
                UpdateRequestStatusDto.builder()
                        .requestIds(List.of(1L))
                        .status(RequestStatus.CONFIRMED)
                        .build()))
                .isInstanceOf(UnavailableUpdateException.class);

        verify(eventFetchClientAdapter, never()).updateConfirmedRequests(anyLong(), any());
    }

    @Test
    void updateRequestStatuses_business404BecomesNotFoundAndCircuitStaysClosed() {
        when(eventFetchClientAdapter.getEventByIdAndUserId(anyLong(), anyLong()))
                .thenReturn(new RemoteCallResult.BusinessFailure<>(404, "event not found"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> requestService.updateRequestStatuses(userId, eventId,
                    UpdateRequestStatusDto.builder()
                            .requestIds(List.of(1L))
                            .status(RequestStatus.REJECTED)
                            .build()))
                    .isInstanceOf(RemoteCallBusinessException.class);
        }

        assertThat(registry.circuitBreaker("event-user-fetch").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        verify(eventFetchClientAdapter, times(5)).getEventByIdAndUserId(userId, eventId);
    }

    @Test
    void updateRequestStatuses_non404BusinessFailurePropagatesAndKeepsCircuitClosed() {
        when(eventFetchClientAdapter.getEventByIdAndUserId(anyLong(), anyLong()))
                .thenReturn(new RemoteCallResult.BusinessFailure<>(400, "bad request"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> requestService.updateRequestStatuses(userId, eventId,
                    UpdateRequestStatusDto.builder()
                            .requestIds(List.of(1L))
                            .status(RequestStatus.REJECTED)
                            .build()))
                    .isInstanceOf(RemoteCallBusinessException.class)
                    .hasMessageContaining("bad request");
        }

        assertThat(registry.circuitBreaker("event-user-fetch").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void updateRequestStatuses_technicalFailuresOpenCircuitAndBlockSixthCall() {
        when(eventFetchClientAdapter.getEventByIdAndUserId(anyLong(), anyLong()))
                .thenReturn(new RemoteCallResult.TechnicalFailure<>("connection refused"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> requestService.updateRequestStatuses(userId, eventId,
                    UpdateRequestStatusDto.builder()
                            .requestIds(List.of(1L))
                            .status(RequestStatus.REJECTED)
                            .build()))
                    .isInstanceOf(RemoteCallTechnicalException.class)
                    .hasMessage("Сервис событий временно недоступен");
        }

        CircuitBreaker cb = registry.circuitBreaker("event-user-fetch");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        assertThatThrownBy(() -> requestService.updateRequestStatuses(userId, eventId,
                UpdateRequestStatusDto.builder()
                        .requestIds(List.of(1L))
                        .status(RequestStatus.REJECTED)
                        .build()))
                .isInstanceOf(CallNotPermittedException.class);

        verify(eventFetchClientAdapter, times(5)).getEventByIdAndUserId(anyLong(), anyLong());
    }
}