package ru.practicum.compilation.service.support;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.compilation.feign.EventFetchClientAdapter;
import ru.practicum.compilation.feign.EventShortDto;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
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
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при получении событий: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка событий: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при получении списка событий: " + tf.reason());
            }
        };
    }

    private List<EventShortDto> getEventShortDtosFallback(List<Long> eventIds, Throwable t) {
        if (t instanceof RemoteCallBusinessException be) {
            throw be;
        }

        if (t instanceof CallNotPermittedException c) {
            log.error("Circuit Breaker открыт для event-fetch. Причина: {}", t.getMessage());
            throw c;
        }

        log.error("event-fetch: технический сбой: {}", t.getMessage());
        throw new RemoteCallTechnicalException("Сервис событий временно недоступен");
    }
}
