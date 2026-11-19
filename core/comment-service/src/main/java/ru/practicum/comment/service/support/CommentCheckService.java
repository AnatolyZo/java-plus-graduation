package ru.practicum.comment.service.support;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.comment.entity.Comment;
import ru.practicum.comment.exception.UnavailableUpdateException;
import ru.practicum.common.Entities;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
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
            log.error("Circuit Breaker открыт для user-checker. Возвращаем заглушку. Причина: {}", t.getMessage());
            throw c;
        }

        log.error("user-checker: технический сбой: {}", t.getMessage());
        throw new RemoteCallTechnicalException("Сервис пользователей временно недоступен");
    }

    @CircuitBreaker(name = "event-checker", fallbackMethod = "checkEventExistenceFallback")
    public Set<Long> checkEventExistence(long eventId) {
        RemoteCallResult<Set<Long>> result = eventExistsClientAdapter.checkExists(List.of(eventId));

        return switch (result) {
            case RemoteCallResult.Success<Set<Long>> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении списка событий: {}", bf.message());
                throw new RemoteCallBusinessException(bf.status(), "Проверьте корректность передаваемых данных, возникла ошибка при получении списка событий: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка событий: {}", tf.reason());
                throw new RemoteCallTechnicalException("Технический сбой при получении списка событий: " + tf.reason());
            }
        };
    }

    private Set<Long> checkEventExistenceFallback(long userId, Throwable t) {
        if (t instanceof RemoteCallBusinessException be) {
            throw be;
        }

        if (t instanceof CallNotPermittedException c) {
            log.error("Circuit Breaker открыт для event-checker. Причина: {}", t.getMessage());
            throw c;
        }

        log.error("event-checker: технический сбой: {}", t.getMessage());
        throw new RemoteCallTechnicalException("Сервис событий временно недоступен");
    }

    public void checkCommentAuthorship(Comment comment, long userId) {
        if (comment.getAuthorId() != userId) {
            throw new UnavailableUpdateException(Entities.COMMENT.name(), comment.getId());
        }
    }
}
