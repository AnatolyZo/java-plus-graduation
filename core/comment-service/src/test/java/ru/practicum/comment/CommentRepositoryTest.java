package ru.practicum.comment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.comment.dto.CommentStatus;
import ru.practicum.comment.entity.Comment;
import ru.practicum.comment.repository.CommentRepository;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CommentRepositoryTest {

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @MockitoBean
    private EventExistsClientAdapter eventExistsClientAdapter;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final long EVENT_ID = 10L;
    private static final long OTHER_EVENT_ID = 20L;

    private void persistComment(String text, long authorId, CommentStatus status, long eventId) {
        entityManager.persist(Comment.builder()
                .text(text)
                .created(LocalDateTime.now().minusMinutes(1))
                .eventId(eventId)
                .authorId(authorId)
                .status(status)
                .build());
    }

    @Test
    @DisplayName("findByEventIdWithOffset: только APPROVED-комментарии нужного события")
    void findByEventIdWithOffset_returnsOnlyApprovedOfRequestedEvent() {
        // комментарии к нашему событию: 3 approved + 1 в модерации
        persistComment("c1", 1L, CommentStatus.APPROVED, EVENT_ID);
        persistComment("c2", 2L, CommentStatus.APPROVED, EVENT_ID);
        persistComment("c3", 3L, CommentStatus.APPROVED, EVENT_ID);
        persistComment("pending", 4L, CommentStatus.PENDING, EVENT_ID);
        // чужое событие и чужой статус не должны попасть
        persistComment("other event", 5L, CommentStatus.APPROVED, OTHER_EVENT_ID);
        entityManager.flush();
        entityManager.clear();

        List<Comment> result = commentRepository.findByEventIdWithOffset(EVENT_ID, 0, 10);

        assertThat(result).hasSize(3);
        assertThat(result)
                .allMatch(c -> c.getStatus() == CommentStatus.APPROVED)
                .allMatch(c -> c.getEventId() == EVENT_ID);
    }

    @Test
    @DisplayName("findByEventIdWithOffset: нарезка страниц по OFFSET/FETCH в порядке id")
    void findByEventIdWithOffset_paginatesInIdOrder() {
        for (int i = 1; i <= 5; i++) {
            persistComment("c" + i, i, CommentStatus.APPROVED, EVENT_ID);
        }
        entityManager.flush();
        entityManager.clear();

        List<Comment> firstPage = commentRepository.findByEventIdWithOffset(EVENT_ID, 0, 2);
        List<Comment> secondPage = commentRepository.findByEventIdWithOffset(EVENT_ID, 2, 2);
        List<Comment> tail = commentRepository.findByEventIdWithOffset(EVENT_ID, 4, 2);

        assertThat(firstPage).extracting(Comment::getText)
                .containsExactly("c1", "c2");
        assertThat(secondPage).extracting(Comment::getText)
                .containsExactly("c3", "c4");
        assertThat(tail).extracting(Comment::getText)
                .containsExactly("c5"); // страница короче size — хвост выборки

        // страницы не пересекаются и вместе дают всю выборку по порядку id
        assertThat(firstPage).extracting(Comment::getId)
                .isSorted();
        assertThat(secondPage).extracting(Comment::getId)
                .isSorted()
                .doesNotContainAnyElementsOf(firstPage.stream().map(Comment::getId).toList());
    }

    @Test
    @DisplayName("findByAuthorId: возвращает комментарии автора независимо от события и статуса")
    void findByAuthorId_returnsAllOfAuthor() {
        persistComment("mine1", 7L, CommentStatus.APPROVED, EVENT_ID);
        persistComment("mine2", 7L, CommentStatus.APPROVED, OTHER_EVENT_ID);
        persistComment("foreign", 8L, CommentStatus.APPROVED, EVENT_ID);
        entityManager.flush();
        entityManager.clear();

        List<Comment> result = commentRepository.findByAuthorId(7L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getAuthorId() == 7L);
    }
}