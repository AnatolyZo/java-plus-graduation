package ru.practicum.comment.service.support;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.comment.entity.Comment;
import ru.practicum.comment.exception.NotFoundException;
import ru.practicum.comment.exception.UnavailableUpdateException;
import ru.practicum.common.Entities;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentCheckService {
    private final UserExistsClientAdapter userExistsClientAdapter;
    private final EventExistsClientAdapter eventExistsClientAdapter;

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

    @CircuitBreaker(name = "event-checker", fallbackMethod = "checkEventExistenceFallback")
    public Long checkEventExistence(long eventId) {
        RemoteCallResult<Set<Long>> result = eventExistsClientAdapter.checkExists(List.of(eventId));

        return switch (result) {
            case RemoteCallResult.Success<Set<Long>> s -> {
                Set<Long> eventIds = s.value();

                if (!eventIds.contains(eventId)) {
                    throw new NotFoundException(Entities.EVENT, eventId);
                }

                yield eventId;
            }
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении списка событий: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении списка событий: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка событий: {}", tf.reason());
                yield 0L;
            }
        };
    }

    private Long checkUserExistenceFallback(long userId, Throwable t) {
        log.error("Circuit Breaker открыт для user-checker. Возвращаем заглушку. Причина: {}", t.getMessage());
        return 0L;
    }

    private Long checkEventExistenceFallback(long userId, Throwable t) {
        log.error("Circuit Breaker открыт для event-checker. Возвращаем заглушку. Причина: {}", t.getMessage());
        return 0L;
    }

    public void checkCommentAuthorship(Comment comment, long userId) {
        if (comment.getAuthorId() != userId) {
            throw new UnavailableUpdateException(Entities.COMMENT.name(), comment.getId());
        }
    }
}
