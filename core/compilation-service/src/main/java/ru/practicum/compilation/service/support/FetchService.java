package ru.practicum.compilation.service.support;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.compilation.feign.EventFetchClientAdapter;
import ru.practicum.compilation.feign.EventShortDto;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FetchService {
    private final EventFetchClientAdapter eventFetchClientAdapter;

    @CircuitBreaker(name = "event-fetch", fallbackMethod = "getEventShortDtosFallback")
    public List<EventShortDto> getEventShortDtos(List<Long> eventIds) {
        RemoteCallResult<List<EventShortDto>> result = eventFetchClientAdapter.getEventShortDtos(eventIds);

        return switch (result) {
            case RemoteCallResult.Success<List<EventShortDto>> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении событий: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении событий: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении событий: {}", tf.reason());
                yield eventIds.stream()
                        .map(id -> EventShortDto.builder().id(0L).build())
                        .toList();
            }
        };
    }

    private List<EventShortDto> getEventShortDtosFallback(List<Long> eventIds, Throwable t) {
        log.error("Circuit Breaker открыт для event-fetch. Возвращаем заглушку. Причина: {}", t.getMessage());
        return eventIds.stream()
                .map(id -> EventShortDto.builder().id(0L).build())
                .toList();
    }
}
