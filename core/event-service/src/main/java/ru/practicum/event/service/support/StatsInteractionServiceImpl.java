package ru.practicum.event.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.practicum.event.dto.event.EventDto;
import ru.practicum.explorewithme.StatsClient;
import ru.practicum.explorewithme.hit.EndpointHitRequest;
import ru.practicum.explorewithme.stats.ViewStatsResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.practicum.common.ControllerConstants.URL_EVENTS;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsInteractionServiceImpl implements StatsInteractionService {
    private final StatsClient statsClient;

    @Value("${spring.application.name}")
    private String appName;

    @Override
    public List<EventDto> getEventsWithStats(List<EventDto> events) {
        Map<String, Long> viewsByUri = getStats(statsClient, events);

        events.forEach(eventDto -> {
            long views = getViews(eventDto, viewsByUri);
            eventDto.setViews(views);
        });
        log.debug("К событиям {} добавлено количество просмотров", events);
        return events;
    }

    @Override
    public void saveHit(String ip, String uri) {
        statsClient.addStatistics(EndpointHitRequest.builder()
                .app(appName)
                .uri(uri)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private Map<String, Long> getStats(StatsClient statsClient, List<EventDto> events) {
        LocalDateTime start = getStartPoint(events);
        LocalDateTime end = LocalDateTime.now();
        List<String> uris = getUris(events);
        List<ViewStatsResponse> stats = statsClient.getStatistics(start, end, uris, true);
        Map<String, Long> viewsByUri = new HashMap<>();
        stats.forEach(stat -> viewsByUri.put(stat.getUri(), stat.getHits()));
        return viewsByUri;
    }

    private long getViews(EventDto event, Map<String, Long> viewsByUri) {
        return viewsByUri.getOrDefault(URL_EVENTS + "/" + event.getId(), 0L);
    }

    private LocalDateTime getStartPoint(List<EventDto> events) {
        return events.stream()
                .map(EventDto::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No events were found"));
    }

    private List<String> getUris(List<EventDto> events) {
        return events.stream()
                .map(event -> URL_EVENTS + "/" + event.getId())
                .toList();
    }
}
