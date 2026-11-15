package ru.practicum.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.practicum.analyzer.entity.EventScoreView;
import ru.practicum.analyzer.entity.UserAction;
import ru.practicum.analyzer.entity.UserActionId;
import ru.practicum.analyzer.repository.UserActionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@DataJpaTest
class UserActionRepositoryTests {
    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserActionRepository repository;

    private void persistAction(long userId, long eventId, double rating, Instant timestamp) {
        em.persist(UserAction.builder()
                .userId(userId)
                .eventId(eventId)
                .rating(rating)
                .timestamp(timestamp)
                .build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // ---------- findViewedActionsOrderedByTimestamp ----------

    @Test
    @DisplayName("возвращает события в порядке убывания timestamp с ограничением")
    void returnsLatestFirstWithLimit() {
        persistAction(1L, 10L, 1.0, BASE);
        persistAction(1L, 20L, 1.0, BASE.plus(5, ChronoUnit.MINUTES));
        persistAction(1L, 30L, 1.0, BASE.plus(3, ChronoUnit.MINUTES));
        persistAction(1L, 40L, 1.0, BASE.plus(2, ChronoUnit.MINUTES));
        flushAndClear();

        List<Long> result = repository.findViewedActionsOrderedByTimestamp(1L, 2);

        assertThat(result).containsExactly(20L, 30L);
    }

    @Test
    @DisplayName("не смешивает действия разных пользователей")
    void filtersByUser() {
        persistAction(1L, 10L, 1.0, BASE);
        persistAction(2L, 99L, 1.0, BASE.plus(1, ChronoUnit.MINUTES));
        flushAndClear();

        List<Long> result = repository.findViewedActionsOrderedByTimestamp(1L, 10);

        assertThat(result).containsExactly(10L);
    }

    @Test
    @DisplayName("нет действий — пустой список")
    void noActionsReturnsEmpty() {
        flushAndClear();

        assertThat(repository.findViewedActionsOrderedByTimestamp(1L, 5)).isEmpty();
    }

    @Test
    @DisplayName("возвращает все просмотренные события пользователя без дубликатов")
    void returnsAllViewedEvents() {
        persistAction(1L, 10L, 1.0, BASE);
        persistAction(1L, 20L, 1.0, BASE.plus(1, ChronoUnit.MINUTES));
        persistAction(2L, 77L, 1.0, BASE.plus(3, ChronoUnit.MINUTES));
        flushAndClear();

        Set<Long> result = repository.findViewedEvents(1L);

        assertThat(result).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("повторное взаимодействие обновляет рейтинг, а не добавляет строку")
    void repeatedInteractionUpdatesExistingRow() {
        persistAction(1L, 10L, 1.0, BASE);
        flushAndClear();

        // обновляем рейтинг той же записи (UPSERT)
        UserAction existing = em.find(UserAction.class, new UserActionId(1L, 10L));
        existing.setRating(4.5);
        flushAndClear();

        assertThat(repository.findViewedEvents(1L)).containsExactly(10L);

        List<EventScoreView> sums = repository.getEventIdsAndRatingSums(List.of(10L));
        assertThat(sums.get(0).getScore()).isEqualTo(4.5, offset(1e-9));
    }

    @Test
    @DisplayName("суммирует рейтинги по событиям, сортирует по сумме по убыванию")
    void sumsRatingsPerEventAndOrdersDesc() {
        persistAction(1L, 10L, 1.5, BASE);
        persistAction(2L, 10L, 0.9, BASE.plus(1, ChronoUnit.MINUTES));
        persistAction(3L, 20L, 2.0, BASE.plus(2, ChronoUnit.MINUTES));
        persistAction(4L, 30L, 5.0, BASE.plus(3, ChronoUnit.MINUTES)); // событие 30 есть в БД, но не в запросе
        flushAndClear();

        List<EventScoreView> result =
                repository.getEventIdsAndRatingSums(List.of(10L, 20L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventId()).isEqualTo(10L); // сумма 2.4 > 2.0
        assertThat(result.get(0).getScore()).isEqualTo(2.4, offset(1e-9));
        assertThat(result.get(1).getEventId()).isEqualTo(20L);
        assertThat(result.get(1).getScore()).isEqualTo(2.0, offset(1e-9));
    }

    @Test
    @DisplayName("событие без действий в списке — не возвращается (нет строки с нулём)")
    void eventWithoutActionsIsAbsent() {
        persistAction(1L, 10L, 1.5, BASE);
        flushAndClear();

        List<EventScoreView> result =
                repository.getEventIdsAndRatingSums(List.of(10L, 99L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo(10L);
    }
}