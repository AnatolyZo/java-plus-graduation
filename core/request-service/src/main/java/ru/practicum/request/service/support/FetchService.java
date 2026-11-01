package ru.practicum.request.service.support;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.common.Entities;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;
import ru.practicum.request.exception.NotFoundException;
import ru.practicum.request.feign.ConfirmedRequestsUpdate;
import ru.practicum.request.feign.EventDto;
import ru.practicum.request.feign.EventFetchClientAdapter;
import ru.practicum.request.feign.EventStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class FetchService {
    private final EventFetchClientAdapter eventFetchClientAdapter;

    @CircuitBreaker(name = "event-user-fetch", fallbackMethod = "getEventByIdUserIdFallback")
    public EventDto getEventByIdAndUserId(long userId, long eventId) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.getEventByIdAndUserId(userId, eventId);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                if (bf.status() == 404) {
                    log.warn("Событие не найдено: {}", bf.message());
                    throw new NotFoundException(Entities.EVENT, eventId);
                }

                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                yield new EventDto(0L, 0, 0L, 0, false, EventStatus.FALLBACK);
            }
        };
    }

    private EventDto getEventByIdUserIdFallback(long userId, long eventId, Throwable t) {
        log.error("Circuit Breaker открыт для event-user-fetch. Возвращаем заглушку. Причина: {}", t.getMessage());
        return new EventDto(0L, 0, 0L, 0, false, EventStatus.FALLBACK);
    }

    @CircuitBreaker(name = "event-fetch", fallbackMethod = "getEventByIdFallback")
    public EventDto getEventById(long eventId) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.getEventById(eventId);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                if (bf.status() == 404) {
                    log.warn("Событие не найдено: {}", bf.message());
                    throw new NotFoundException(Entities.EVENT, eventId);
                }

                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                yield new EventDto(0L, 0, 0L, 0, false, EventStatus.FALLBACK);
            }
        };
    }

    private EventDto getEventByIdFallback(long eventId, Throwable t) {
        log.error("Circuit Breaker открыт для event-fetch. Возвращаем заглушку. Причина: {}", t.getMessage());
        return new EventDto(0L, 0, 0L, 0, false, EventStatus.FALLBACK);
    }

    @CircuitBreaker(name = "event-update", fallbackMethod = "updateConfirmedRequestsFallback")
    public EventDto updateConfirmedRequests(long eventId, ConfirmedRequestsUpdate confirmedRequestsUpdate) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.updateConfirmedRequests(eventId, confirmedRequestsUpdate);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при обновлении доступных мест события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при обновлении доступных мест события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при обновлении доступных мест события: {}", tf.reason());
                yield  new EventDto(0L, 0, 0L, 0, false, EventStatus.FALLBACK);
            }
        };
    }

    private EventDto updateConfirmedRequestsFallback(long eventId, ConfirmedRequestsUpdate confirmedRequestsUpdate, Throwable t) {
        log.error("Circuit Breaker открыт для event-update. Возвращаем заглушку. Причина: {}", t.getMessage());
        return new EventDto(0L, 0, 0L, 0, false, EventStatus.FALLBACK);
    }
}
