package ru.practicum.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.practicum.event.dto.event.EventDto;
import ru.practicum.event.service.support.StatsInteractionServiceImpl;
import ru.practicum.explorewithme.StatsClient;
import ru.practicum.explorewithme.hit.EndpointHitRequest;
import ru.practicum.explorewithme.stats.ViewStatsResponse;

@ExtendWith(MockitoExtension.class)
public class StatsInteractionServiceImplTests {
    private static final String APP_NAME = "test-app";
    private static final long EVENT_ID_1 = 1L;
    private static final long EVENT_ID_2 = 2L;

    @Mock
    private StatsClient statsClient;

    @InjectMocks
    private StatsInteractionServiceImpl service;

    private EventDto event1;
    private EventDto event2;
    private List<EventDto> events;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdOn1 = now.minusDays(2);
        LocalDateTime createdOn2 = now.minusDays(1);

        event1 = EventDto.builder()
                .id(EVENT_ID_1)
                .title("Event 1")
                .createdOn(createdOn1)
                .build();

        event2 = EventDto.builder()
                .id(EVENT_ID_2)
                .title("Event 2")
                .createdOn(createdOn2)
                .build();

        events = List.of(event1, event2);
    }

    @Test
    void getEventsWithStats_assignsViewsCorrectly() {
        String uri1 = "/events/" + EVENT_ID_1;
        String uri2 = "/events/" + EVENT_ID_2;

        ViewStatsResponse stat1 = ViewStatsResponse.builder()
                .uri(uri1)
                .hits(10L)
                .build();

        ViewStatsResponse stat2 = ViewStatsResponse.builder()
                .uri(uri2)
                .hits(25L)
                .build();

        List<ViewStatsResponse> statsResponse = List.of(stat1, stat2);

        when(statsClient.getStatistics(any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyList(),
                eq(true)))
                .thenReturn(statsResponse);

        List<EventDto> result = service.getEventsWithStats(events);

        Assertions.assertThat(result).isSameAs(events);
        Assertions.assertThat(result).hasSize(2);

        EventDto eventWithId1 = result.stream()
                .filter(e -> e.getId() == EVENT_ID_1)
                .findFirst()
                .orElseThrow();

        EventDto eventWithId2 = result.stream()
                .filter(e -> e.getId() == EVENT_ID_2)
                .findFirst()
                .orElseThrow();

        Assertions.assertThat(eventWithId1.getViews()).isEqualTo(10L);
        Assertions.assertThat(eventWithId2.getViews()).isEqualTo(25L);
    }

    @Test
    void getEventsWithStats_emptyList_throwsException() {
        List<EventDto> emptyEvents = List.of();

        Assertions.assertThatThrownBy(() -> service.getEventsWithStats(emptyEvents))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void saveHit_sendsCorrectRequestToStatsClient() {
        String ip = "192.168.0.1";
        String uri = "/events/123";

        service.saveHit(ip, uri);

        ArgumentCaptor<EndpointHitRequest> captor = ArgumentCaptor.forClass(EndpointHitRequest.class);
        verify(statsClient).addStatistics(captor.capture());

        EndpointHitRequest request = captor.getValue();

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getIp()).isEqualTo(ip);
        Assertions.assertThat(request.getUri()).isEqualTo(uri);
        Assertions.assertThat(request.getTimestamp()).isNotNull();
    }

    @Test
    void saveHit_verifiesCorrectRequestSent() {
        String ip = "10.0.0.5";
        String uri = "/events/456";

        service.saveHit(ip, uri);

        verify(statsClient, times(1)).addStatistics(any());

        ArgumentCaptor<EndpointHitRequest> captor = ArgumentCaptor.forClass(EndpointHitRequest.class);
        verify(statsClient).addStatistics(captor.capture());

        EndpointHitRequest capturedRequest = captor.getValue();

        Assertions.assertThat(capturedRequest).isNotNull();
        Assertions.assertThat(capturedRequest.getIp()).isEqualTo(ip);
        Assertions.assertThat(capturedRequest.getUri()).isEqualTo(uri);
        Assertions.assertThat(capturedRequest.getTimestamp()).isNotNull();
    }
}
