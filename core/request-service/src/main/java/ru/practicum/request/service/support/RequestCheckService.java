package ru.practicum.request.service.support;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.common.Entities;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;
import ru.practicum.request.exception.DuplicatedDataException;
import ru.practicum.request.exception.NotFoundException;
import ru.practicum.request.exception.UnavailableUpdateException;
import ru.practicum.request.feign.EventDto;
import ru.practicum.request.feign.EventStatus;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestCheckService {
    private final EventExistsClientAdapter eventExistsClientAdapter;
    private final UserExistsClientAdapter userExistsClientAdapter;

    public void requestChecks(EventDto event, long userId) {
        if (event.initiator() == userId) {
            throw new DuplicatedDataException(Entities.REQUEST.name(), "eventId and userId", event.id());
        }

        if (!event.state().equals(EventStatus.PUBLISHED)) {
            throw new UnavailableUpdateException(Entities.EVENT.name(), event.id());
        }

        if (event.participantLimit() - event.confirmedRequests() == 0 && event.participantLimit() != 0) {
            throw new UnavailableUpdateException(Entities.EVENT.name(), event.id());
        }
    }

    @CircuitBreaker(name = "event-checker", fallbackMethod = "checkEventExistenceFallback")
    public boolean checkEventExistence(long userId, long eventId) {
        RemoteCallResult<Boolean> result = eventExistsClientAdapter.checkExistenceWithInitiatorId(eventId, userId);

        return switch (result) {
            case RemoteCallResult.Success<Boolean> s -> {
                Boolean isEventExists = s.value();

                if (!isEventExists) {
                    throw new NotFoundException(Entities.EVENT, eventId);
                }

                yield true;
            }
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                yield false;
            }
        };
    }

    private boolean checkEventExistenceFallback(long userId, long eventId, Throwable t) {
        log.error("Circuit Breaker открыт для event-checker. Возвращаем заглушку. Причина: {}", t.getMessage());
        return false;
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
}
