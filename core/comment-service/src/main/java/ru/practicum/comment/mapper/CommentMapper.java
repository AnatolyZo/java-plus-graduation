package ru.practicum.comment.mapper;


import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.CommentShortDto;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.entity.Comment;

public class CommentMapper {
    public static CommentShortDto toCommentShortDto(Comment comment) {
        return CommentShortDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .created(comment.getCreated())
                .updated(comment.getUpdated())
                .eventId(comment.getEventId())
                .authorId(comment.getAuthorId())
                .build();
    }

    public static CommentDto toCommentDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .created(comment.getCreated())
                .updated(comment.getUpdated())
                .eventId(comment.getEventId())
                .authorId(comment.getAuthorId())
                .status(comment.getStatus())
                .moderatorId(comment.getModeratorId() != null ? comment.getModeratorId() : null)
                .moderated(comment.getModerated())
                .build();
    }

    public static Comment toComment(NewCommentDto dto) {
        return Comment.builder()
                .textOnModeration(dto.getText())
                .build();
    }
}