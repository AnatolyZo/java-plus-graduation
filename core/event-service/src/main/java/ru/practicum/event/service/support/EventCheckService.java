package ru.practicum.event.service.support;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.common.Entities;
import ru.practicum.event.dto.event.AdminUpdateEventDto;
import ru.practicum.event.dto.event.EventStatus;
import ru.practicum.event.entity.Event;
import ru.practicum.event.exception.*;
import ru.practicum.event.feign.RequestExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
import ru.practicum.feign.result.RemoteCallResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCheckService {
    private final UserExistsClientAdapter userExistsClientAdapter;
    private final RequestExistsClientAdapter requestExistsClientAdapter;
    private static final int ADMIN_MIN_OFFSET = 1;

    public void checkDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new WrongDateIntervalException("rangeStart must be before rangeEnd");
        }
    }

    public void checkEventNotCanceled(Event event) {
        if (!event.getStatus().equals(EventStatus.CANCELED) && !event.getStatus().equals(EventStatus.PENDING)) {
            throw new UnavailableUpdateException(Entities.EVENT.name(), event.getId());
        }
    }

    public void checkEventNotPublished(Event event) {
        if (!event.getStatus().equals(EventStatus.PENDING)) {
            throw new UnavailableUpdateException(Entities.EVENT.name(), event.getId());
        }
    }

    @CircuitBreaker(name = "user-checker", fallbackMethod = "checkUserExistenceFallback")
    public Set<Long> checkUserExistence(long userId) {
        RemoteCallResult<Set<Long>> result = userExistsClientAdapter.checkExists(List.of(userId));

        return switch (result) {
            case RemoteCallResult.Success<Set<Long>> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении списка пользователей: {}", bf.message());
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при получении списка пользователей: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка пользователей: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при получении списка пользователей: " + tf.reason());
            }
        };
    }

    private Set<Long> checkUserExistenceFallback(long userId, Throwable t) {
        if (t instanceof RemoteCallBusinessException be) {
            throw be;
        }

        if (t instanceof CallNotPermittedException c) {
            log.error("Circuit Breaker открыт для user-checker. Причина: {}", t.getMessage());
            throw c;
        }

        log.error("user-checker: технический сбой: {}", t.getMessage());
        throw new RemoteCallTechnicalException("Сервис пользователей временно недоступен");
    }

    public void checkUpdatingEventDate(Event event, AdminUpdateEventDto update) {
        if (event.getPublishedOn() != null) {
            if (update.getEventDate() != null && !update.getEventDate().isAfter(event.getPublishedOn().plusHours(ADMIN_MIN_OFFSET))) {
                throw new EarlyDateException(ADMIN_MIN_OFFSET, event.getEventDate());
            }
            //Обработка случая, если событие изменяется, но не опубликовано
        } else {
            if (update.getEventDate() != null && !update.getEventDate().isAfter(event.getCreatedOn().plusHours(ADMIN_MIN_OFFSET))) {
                throw new EarlyDateException(ADMIN_MIN_OFFSET, event.getEventDate());
            }
        }
    }

    @CircuitBreaker(name = "request-checker", fallbackMethod = "checkRequestExistenceFallback")
    public boolean checkRequestExistence(long eventId, long userId) {
        RemoteCallResult<Boolean> result = requestExistsClientAdapter.checkExistenceByEventIdAndRequesterId(eventId, userId);

        return switch (result) {
            case RemoteCallResult.Success<Boolean> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при проверке существования заявки на участие в мероприятии: {}", bf.message());
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при проверке существования заявки на участие в мероприятии: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при проверке существования заявки на участие в мероприятии: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при проверке существования заявки на участие в мероприятии: " + tf.reason());
            }
        };
    }

    private boolean checkRequestExistenceFallback(long eventId, long userId, Throwable t) {
        if (t instanceof RemoteCallBusinessException be) {
            throw be;
        }

        if (t instanceof CallNotPermittedException c) {
            log.error("Circuit Breaker открыт для request-checker. Возвращаем заглушку. Причина: {}", t.getMessage());
            return false;
        }

        log.error("request-checker: технический сбой: {}", t.getMessage());
        return false;
    }
}
