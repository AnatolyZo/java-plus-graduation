package ru.practicum.request.service.support;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
import ru.practicum.feign.result.RemoteCallResult;
import ru.practicum.request.feign.ConfirmedRequestsUpdate;
import ru.practicum.request.feign.EventDto;
import ru.practicum.request.feign.EventFetchClientAdapter;

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
                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при получении события: " + tf.reason());
            }
        };
    }

    private EventDto getEventByIdUserIdFallback(long userId, long eventId, Throwable t) {
        if (t instanceof RemoteCallBusinessException be) {
            throw be;
        }

        if (t instanceof CallNotPermittedException c) {
            log.error("Circuit Breaker открыт для event-user-fetch. Причина: {}", t.getMessage());
            throw c;
        }

        log.error("event-user-fetch: технический сбой: {}", t.getMessage());
        throw new RemoteCallTechnicalException("Сервис событий временно недоступен");
    }

    @CircuitBreaker(name = "event-fetch", fallbackMethod = "getEventByIdFallback")
    public EventDto getEventById(long eventId) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.getEventById(eventId);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при получении события: " + tf.reason());
            }
        };
    }

    private EventDto getEventByIdFallback(long eventId, Throwable t) {
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

    @CircuitBreaker(name = "event-update", fallbackMethod = "updateConfirmedRequestsFallback")
    public EventDto updateConfirmedRequests(long eventId, ConfirmedRequestsUpdate confirmedRequestsUpdate) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.updateConfirmedRequests(eventId, confirmedRequestsUpdate);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при обновлении доступных мест события: {}", bf.message());
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при обновлении доступных мест события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при обновлении доступных мест события: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при обновлении доступных мест события: " + tf.reason());
            }
        };
    }

    private EventDto updateConfirmedRequestsFallback(long eventId, ConfirmedRequestsUpdate confirmedRequestsUpdate, Throwable t) {
        if (t instanceof RemoteCallBusinessException be) {
            throw be;
        }

        if (t instanceof CallNotPermittedException c) {
            log.error("Circuit Breaker открыт для event-update. Причина: {}", t.getMessage());
            throw c;
        }

        log.error("event-update: технический сбой: {}", t.getMessage());
        throw new RemoteCallTechnicalException("Сервис событий временно недоступен");
    }
}
