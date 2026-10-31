package ru.practicum.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.comment.dto.*;
import ru.practicum.comment.entity.Comment;
import ru.practicum.comment.exception.NotFoundException;
import ru.practicum.comment.exception.UnavailableUpdateException;
import ru.practicum.comment.mapper.CommentMapper;
import ru.practicum.comment.pagination.OffsetPageRequest;
import ru.practicum.comment.repository.AdminCommentSearchSpecification;
import ru.practicum.comment.repository.CommentRepository;
import ru.practicum.common.Entities;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserExistsClientAdapter userExistsClientAdapter;
    private final EventExistsClientAdapter eventExistsClientAdapter;

    @Override
    public List<CommentShortDto> getComments(long eventId, int from, int size) {
        log.trace("Иницировано получение комментариев с параметрами from={} и size={}", from, size);
        List<Comment> result = commentRepository.findByEventIdWithOffset(eventId, from, size);
        log.debug("Найдено {} категорий", result.size());
        return result.stream()
                .map(CommentMapper::toCommentShortDto)
                .toList();
    }

    @Override
    public List<CommentDto> searchComments(long adminId,
                                           String text,
                                           List<Long> authorsIds,
                                           String rangeStart,
                                           String rangeEnd,
                                           List<Long> eventIds,
                                           List<CommentStatus> states,
                                           int from,
                                           int size) {
        log.trace("Иницирован поиск комментариев");
        Specification<Comment> spec = new AdminCommentSearchSpecification(text, authorsIds, rangeStart, rangeEnd, eventIds, states);
        Pageable pageable = new OffsetPageRequest(from, size);
        Page<Comment> commentPage = commentRepository.findAll(spec, pageable);
        List<Comment> comments = commentPage.getContent();
        log.debug("Получен список комментариев {}", comments);
        if (comments.isEmpty()) {
            return List.of();
        }

        return comments.stream()
                .map(CommentMapper::toCommentDto)
                .toList();
    }

    @Override
    @Transactional
    public CommentDto moderateComment(long adminId, long commentId, ModerationAction action) {
        log.trace("Иницирована модерация комментария с id {} администратором с id {}", commentId, adminId);
        LocalDateTime now = LocalDateTime.now();
        checkUserExistence(adminId);
        Comment comment = findCommentById(commentId);

        if (action.equals(ModerationAction.APPROVE)) {
            comment.setText(comment.getTextOnModeration());
            comment.setUpdated(now);
            comment.setStatus(CommentStatus.APPROVED);
        }

        if (action.equals(ModerationAction.REJECT) && comment.getText() == null) {
            comment.setStatus(CommentStatus.REJECTED);
        } else if (action.equals(ModerationAction.REJECT) && comment.getText() != null) {
            comment.setStatus(CommentStatus.APPROVED);
        }

        comment.setModeratorId(adminId);
        comment.setModerated(now);
        comment.setTextOnModeration(null);
        commentRepository.save(comment);
        log.debug("Комментарий после модерации {}", comment);
        return CommentMapper.toCommentDto(comment);
    }

    @Override
    @Transactional
    public void deleteCommentByAdmin(long adminId, long commentId) {
        log.trace("Иницировано удаление комментария с id {} администратором с id {}", commentId, adminId);
        checkUserExistence(adminId);
        Comment comment = findCommentById(commentId);
        commentRepository.delete(comment);
        log.debug("Удален комметарий {}", comment);
    }

    @Override
    @Transactional
    public void deleteCommentByUser(long userId, long commentId) {
        log.trace("Иницировано удаление комментария с id {} пользователем с id {}", commentId, userId);
        checkUserExistence(userId);
        Comment comment = findCommentById(commentId);
        checkCommentAuthorship(comment, userId);
        commentRepository.delete(comment);
        log.debug("Удален комметарий {}", comment);
    }

    @Override
    public List<CommentShortDto> getUsersComments(long userId) {
        log.trace("Иницировано получение комментариев пользователя с id {}", userId);
        checkUserExistence(userId);
        List<Comment> commentsList = commentRepository.findByAuthorId(userId);
        log.debug("Получены кооментарии пользователя {}", commentsList);
        return commentsList.stream()
                .map(CommentMapper::toCommentShortDto)
                .toList();
    }

    @Override
    @Transactional
    public CommentShortDto createComment(long userId, long eventId, NewCommentDto body) {
        log.trace("Иницировано создание комментария к событию с id {} пользователем с id {}", eventId, userId);
        checkUserExistence(userId);
        checkEventExistence(eventId);
        Comment comment = CommentMapper.toComment(body);
        setFieldsOnCreation(comment, eventId, userId);
        Comment createdComment = commentRepository.save(comment);
        log.info("Создан комментарий {}", createdComment);
        return CommentMapper.toCommentShortDto(createdComment);
    }

    @Override
    @Transactional
    public CommentShortDto updateComment(long userId, long commentId, UpdateCommentDto body) {
        log.trace("Иницировано обновление комментария с id {} пользователем с id {}", commentId, userId);
        checkUserExistence(userId);
        Comment comment = findCommentById(commentId);
        checkCommentAuthorship(comment, userId);
        comment.setTextOnModeration(body.getText());
        comment.setStatus(CommentStatus.PENDING);
        Comment updatedComment = commentRepository.save(comment);
        log.info("Обновлен комментарий {}", updatedComment);
        return CommentMapper.toCommentShortDto(updatedComment);
    }

    private void setFieldsOnCreation(Comment comment, Long eventId, Long authorId) {
        comment.setCreated(LocalDateTime.now());
        comment.setEventId(eventId);
        comment.setAuthorId(authorId);
        comment.setStatus(CommentStatus.PENDING);
    }

    private void checkUserExistence(long userId) {
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

    private void checkEventExistence(long eventId) {
        RemoteCallResult<Set<Long>> result = eventExistsClientAdapter.checkExists(List.of(eventId));

        switch (result) {
            case RemoteCallResult.Success<Set<Long>> s -> {
                Set<Long> eventIds = s.value();

                if (!eventIds.contains(eventId)) {
                    throw new NotFoundException(Entities.EVENT, eventId);
                }
            }
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении списка событий: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении списка событий: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении списка событий: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при получении списка событий: " + tf.reason());
            }
        }
    }

    private void checkCommentAuthorship(Comment comment, long userId) {
        if (comment.getAuthorId() != userId) {
            throw new UnavailableUpdateException(Entities.COMMENT.name(), comment.getId());
        }
    }

    private Comment findCommentById(long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(Entities.COMMENT, commentId));
    }
}
