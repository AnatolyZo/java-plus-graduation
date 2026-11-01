package ru.practicum.event.service.support;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.common.Entities;
import ru.practicum.event.dto.event.AdminUpdateEventDto;
import ru.practicum.event.dto.event.EventStatus;
import ru.practicum.event.entity.Event;
import ru.practicum.event.exception.EarlyDateException;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.exception.UnavailableUpdateException;
import ru.practicum.event.exception.WrongDateIntervalException;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCheckService {
    private final UserExistsClientAdapter userExistsClientAdapter;
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
    public Long checkUserExistence(long userId) {
        RemoteCallResult<Set<Long>> result = userExistsClientAdapter.checkExists(List.of(userId));

        return switch (result) {
            case RemoteCallResult.Success<Set<Long>> s -> {
                Set<Long> usersIds = s.value();

                if (!usersIds.contains(userId)) {
                    throw new NotFoundException(Entities.USER, userId);
                }

                yield userId;
            }
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении списка пользователей: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении списка пользователей: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка пользователей: {}", tf.reason());
                yield 0L;
            }
        };
    }

    private Long checkUserExistenceFallback(long userId, Throwable t) {
        log.error("Circuit Breaker открыт для user-checker. Возвращаем заглушку. Причина: {}", t.getMessage());
        return 0L;
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
}
