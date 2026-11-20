package ru.practicum.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.practicum.analyzer.entity.EventScoreView;
import ru.practicum.analyzer.entity.EventSimilarity;
import ru.practicum.analyzer.entity.NeighborView;
import ru.practicum.analyzer.entity.UserAction;
import ru.practicum.analyzer.repository.EventSimilarityRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@DataJpaTest
public class EventSimilarityRepositoryTests {
    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EventSimilarityRepository repository;

    private void persistSimilarity(long eventA, long eventB, double score) {
        em.persist(EventSimilarity.builder()
                .eventA(eventA)
                .eventB(eventB)
                .score(score)
                .timestamp(BASE)
                .build());
    }

    private void persistAction(long userId, long eventId, double rating, int minute) {
        em.persist(UserAction.builder()
                .userId(userId)
                .eventId(eventId)
                .rating(rating)
                .timestamp(BASE.plus(minute, ChronoUnit.MINUTES))
                .build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // ---------- findSimilaritiesById ----------

    @Test
    @DisplayName("возвращает пары, где событие стоит в eventA или в eventB")
    void findsSimilaritiesEitherSide() {
        persistSimilarity(1L, 2L, 0.5);
        persistSimilarity(3L, 1L, 0.9);
        persistSimilarity(4L, 5L, 0.1); // не связана с событием 1
        flushAndClear();

        List<EventSimilarity> result = repository.findSimilaritiesById(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EventSimilarity::getScore)
                .containsExactlyInAnyOrder(0.5, 0.9);
    }

    @Test
    @DisplayName("нет пар для события — пустой список")
    void noSimilaritiesForEvent() {
        flushAndClear();

        assertThat(repository.findSimilaritiesById(999L)).isEmpty();
    }

    // ---------- findSimilarNotInteracted ----------

    @Test
    @DisplayName("исключает события, с которыми пользователь уже взаимодействовал")
    void excludesInteractedEvents() {
        persistSimilarity(1L, 2L, 0.5);
        persistSimilarity(1L, 3L, 0.9);
        persistSimilarity(1L, 4L, 0.8);
        persistAction(42L, 3L, 5.0, 1);
        flushAndClear();

        List<EventScoreView> result =
                repository.findSimilarNotInteracted(List.of(1L), 42L, 5);

        assertThat(result).extracting(EventScoreView::getEventId)
                .containsExactlyInAnyOrder(2L, 4L);
    }

    @Test
    @DisplayName("сортирует по score по убыванию и ограничивает лимитом")
    void ordersByScoreAndLimits() {
        persistSimilarity(1L, 2L, 0.5);
        persistSimilarity(1L, 3L, 0.9);
        persistSimilarity(1L, 4L, 0.8);
        flushAndClear();

        List<EventScoreView> result =
                repository.findSimilarNotInteracted(List.of(1L), 42L, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventId()).isEqualTo(3L); // 0.9
        assertThat(result.get(1).getEventId()).isEqualTo(4L); // 0.8
        assertThat(result.get(0).getScore()).isEqualTo(0.9, offset(1e-9));
    }

    @Test
    @DisplayName("когда запрошенное событие в eventB, соседом считается eventA")
    void picksEventASideWhenQueriedEventIsB() {
        persistSimilarity(6L, 1L, 0.7);
        persistAction(42L, 1L, 4.0, 1);
        flushAndClear();

        List<EventScoreView> result =
                repository.findSimilarNotInteracted(List.of(1L), 42L, 5);

        assertThat(result).extracting(EventScoreView::getEventId)
                .containsExactly(6L);
    }

    @Test
    @DisplayName("взаимодействия ДРУГОГО пользователя не мешают результату")
    void otherUsersInteractionsDoNotFilter() {
        persistSimilarity(1L, 2L, 0.5);
        persistAction(43L, 2L, 5.0, 1);
        flushAndClear();

        List<EventScoreView> result =
                repository.findSimilarNotInteracted(List.of(1L), 42L, 5);

        assertThat(result).extracting(EventScoreView::getEventId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("оба конца пары просмотрены — пары нет в результате")
    void bothEndsInteractedExcluded() {
        persistSimilarity(1L, 2L, 0.5);
        persistAction(42L, 1L, 3.0, 1);
        persistAction(42L, 2L, 4.0, 2);
        flushAndClear();

        List<EventScoreView> result =
                repository.findSimilarNotInteracted(List.of(1L), 42L, 5);

        assertThat(result).isEmpty();
    }

    // ---------- findNearestNeighbors ----------

    @Test
    @DisplayName("возвращает только соседей, с которыми пользователь взаимодействовал, в порядке убывания similarity")
    void returnsInteractedNeighborsBySimilarityDesc() {
        persistSimilarity(1L, 2L, 0.9);
        persistSimilarity(1L, 3L, 0.8);
        persistSimilarity(1L, 4L, 0.7);
        persistSimilarity(1L, 5L, 0.6);
        persistSimilarity(1L, 6L, 0.5);
        persistSimilarity(1L, 7L, 0.4); // седьмой сосед — за пределом k=5
        persistSimilarity(1L, 8L, 0.99); // похож сильнее всех, но пользователь с ним не взаимодействовал

        persistAction(42L, 2L, 5.0, 1);
        persistAction(42L, 3L, 4.0, 2);
        persistAction(42L, 4L, 3.0, 3);
        persistAction(42L, 5L, 2.0, 4);
        persistAction(42L, 6L, 1.0, 5);
        flushAndClear();

        List<NeighborView> result =
                repository.findNearestNeighbors(1L, 42L, 5);

        assertThat(result).extracting(NeighborView::getEventId)
                .containsExactly(2L, 3L, 4L, 5L, 6L);
        assertThat(result.get(0).getRating()).isEqualTo(5.0, offset(1e-9));
        assertThat(result.get(0).getSimilarity()).isEqualTo(0.9, offset(1e-9));
    }

    @Test
    @DisplayName("не включает взаимодействия других пользователей")
    void ignoresOtherUsersActions() {
        persistSimilarity(1L, 2L, 0.9);
        persistAction(43L, 2L, 5.0, 1);
        flushAndClear();

        List<NeighborView> result =
                repository.findNearestNeighbors(1L, 42L, 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("нет соседей — пустой список")
    void noNeighborsReturnsEmpty() {
        flushAndClear();

        assertThat(repository.findNearestNeighbors(1L, 42L, 5)).isEmpty();
    }
}
