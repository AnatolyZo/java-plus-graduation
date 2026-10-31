package ru.practicum.request.service.support;

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

    public void checkEventExistence(long userId, long eventId) {
        RemoteCallResult<Boolean> result = eventExistsClientAdapter.checkExistenceWithInitiatorId(eventId, userId);

        switch (result) {
            case RemoteCallResult.Success<Boolean> s -> {
                Boolean isEventExists = s.value();

                if (!isEventExists) {
                    throw new NotFoundException(Entities.EVENT, eventId);
                }
            }
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при получении события: " + tf.reason());
            }
        }
    }

    public void checkUserExistence(long userId) {
        RemoteCallResult<Set<Long>> result = userExistsClientAdapter.checkExists(List.of(userId));

        switch (result) {
            case RemoteCallResult.Success<Set<Long>> s -> {
                Set<Long> usersIds = s.value();

                if (!usersIds.contains(userId)) {
                    throw new NotFoundException(Entities.USER, userId);
                }
            }
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении списка пользователей: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении списка пользователей: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка пользователей: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при получении списка пользователей: " + tf.reason());
            }
        }
    }
}
