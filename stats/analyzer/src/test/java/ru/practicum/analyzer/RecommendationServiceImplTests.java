package ru.practicum.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.analyzer.entity.EventScoreView;
import ru.practicum.analyzer.entity.NeighborView;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.analyzer.repository.UserActionRepository;
import ru.practicum.analyzer.service.RecommendationServiceImpl;
import ru.practicum.analyzer.entity.EventSimilarity;
import ru.practicum.grpc.stats.service.dashboard.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.service.dashboard.RecommendedEventProto;
import ru.practicum.grpc.stats.service.dashboard.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.service.dashboard.UserPredictionsRequestProto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTests {

    @Mock
    private EventSimilarityRepository eventSimilarityRepository;

    @Mock
    private UserActionRepository userActionRepository;

    @InjectMocks
    private RecommendationServiceImpl service;

    private EventScoreView scoreView(long eventId) {
        EventScoreView view = mock(EventScoreView.class);
        when(view.getEventId()).thenReturn(eventId);
        return view;
    }

    private NeighborView neighbor(double rating, double similarity) {
        NeighborView view = mock(NeighborView.class);
        when(view.getRating()).thenReturn(rating);
        when(view.getSimilarity()).thenReturn(similarity);
        return view;
    }

    private EventSimilarity similarity(long eventA, long eventB, double score) {
        EventSimilarity sim = new EventSimilarity();
        sim.setEventA(eventA);
        sim.setEventB(eventB);
        sim.setScore(score);
        return sim;
    }

    private EventSimilarity similarity(long eventA, long eventB) {
        return similarity(eventA, eventB, 0.0);
    }

    // ---------- getRecommendationsForUser ----------

    @Test
    @DisplayName("нет просмотров пользователя — пустой список, similarity-репозиторий не трогаем")
    void noViewedEventsReturnsEmpty() {
        when(userActionRepository.findViewedActionsOrderedByTimestamp(1L, 5)).thenReturn(List.of());

        List<RecommendedEventProto> result = service.getRecommendationsForUser(
                UserPredictionsRequestProto.newBuilder().setUserId(1).setMaxResults(5).build());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("просмотры есть, но похожих не взаимодействованных событий нет — пустой список")
    void noSimilaritiesReturnsEmpty() {
        when(userActionRepository.findViewedActionsOrderedByTimestamp(1L, 5)).thenReturn(List.of(10L));
        when(eventSimilarityRepository.findSimilarNotInteracted(List.of(10L), 1L, 5)).thenReturn(List.of());

        List<RecommendedEventProto> result = service.getRecommendationsForUser(
                UserPredictionsRequestProto.newBuilder().setUserId(1).setMaxResults(5).build());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("один сосед: score = рейтинг соседа (при sum(sim) = sim)")
    void singleNeighborScoreEqualsRating() {
        when(userActionRepository.findViewedActionsOrderedByTimestamp(1L, 5)).thenReturn(List.of(1L));

        EventScoreView similarity = scoreView(2L);
        List<NeighborView> neighbors = List.of(neighbor(0.4, 0.8));

        when(eventSimilarityRepository.findSimilarNotInteracted(List.of(1L), 1L, 5))
                .thenReturn(List.of(similarity));
        when(eventSimilarityRepository.findNearestNeighbors(2L, 1L, 5))
                .thenReturn(neighbors);

        List<RecommendedEventProto> result = service.getRecommendationsForUser(
                UserPredictionsRequestProto.newBuilder().setUserId(1).setMaxResults(5).build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo(2L);
        assertThat(result.get(0).getScore()).isEqualTo(0.4, offset(1e-9));
    }

    @Test
    @DisplayName("несколько соседей: взвешенное среднее = sum(rating*sim) / sum(sim)")
    void multipleNeighborsWeightedAverage() {
        when(userActionRepository.findViewedActionsOrderedByTimestamp(1L, 5)).thenReturn(List.of(1L));

        EventScoreView similarity = scoreView(2L);
        List<NeighborView> neighbors = List.of(neighbor(1.0, 0.5), neighbor(0.8, 0.5));

        when(eventSimilarityRepository.findSimilarNotInteracted(List.of(1L), 1L, 5))
                .thenReturn(List.of(similarity));
        when(eventSimilarityRepository.findNearestNeighbors(2L, 1L, 5))
                .thenReturn(neighbors);
        // (1.0*0.5 + 0.8*0.5) / (0.5 + 0.5) = 0.9

        List<RecommendedEventProto> result = service.getRecommendationsForUser(
                UserPredictionsRequestProto.newBuilder().setUserId(1).setMaxResults(5).build());

        assertThat(result.get(0).getScore()).isEqualTo(0.9, offset(1e-9));
    }

    @Test
    @DisplayName("сумма коэффициентов соседей = 0 — score = 0.0 без деления на ноль")
    void zeroNeighborSimilaritySumGivesZero() {
        when(userActionRepository.findViewedActionsOrderedByTimestamp(1L, 5)).thenReturn(List.of(1L));

        EventScoreView similarity = scoreView(2L);
        when(eventSimilarityRepository.findSimilarNotInteracted(List.of(1L), 1L, 5))
                .thenReturn(List.of(similarity));
        when(eventSimilarityRepository.findNearestNeighbors(2L, 1L, 5)).thenReturn(List.of());

        List<RecommendedEventProto> result = service.getRecommendationsForUser(
                UserPredictionsRequestProto.newBuilder().setUserId(1).setMaxResults(5).build());

        assertThat(result.get(0).getScore()).isEqualTo(0.0, offset(1e-9));
    }

    @Test
    @DisplayName("несколько похожих событий — по прогнозу на каждое")
    void multipleSimilarEventsEachGetScore() {
        when(userActionRepository.findViewedActionsOrderedByTimestamp(1L, 5)).thenReturn(List.of(1L));

        EventScoreView similarityForEvent2 = scoreView(2L);
        EventScoreView similarityForEvent3 = scoreView(3L);
        List<NeighborView> neighborsForEvent2 = List.of(neighbor(0.4, 0.5));
        List<NeighborView> neighborsForEvent3 = List.of(neighbor(0.6, 0.3));

        when(eventSimilarityRepository.findSimilarNotInteracted(List.of(1L), 1L, 5))
                .thenReturn(List.of(similarityForEvent2, similarityForEvent3));
        when(eventSimilarityRepository.findNearestNeighbors(2L, 1L, 5))
                .thenReturn(neighborsForEvent2);
        when(eventSimilarityRepository.findNearestNeighbors(3L, 1L, 5))
                .thenReturn(neighborsForEvent3);

        List<RecommendedEventProto> result = service.getRecommendationsForUser(
                UserPredictionsRequestProto.newBuilder().setUserId(1).setMaxResults(5).build());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventId()).isEqualTo(2L);
        assertThat(result.get(0).getScore()).isEqualTo(0.4, offset(1e-9));
        assertThat(result.get(1).getEventId()).isEqualTo(3L);
        assertThat(result.get(1).getScore()).isEqualTo(0.6, offset(1e-9));
    }

    // ---------- getSimilarEvents ----------

    @Test
    @DisplayName("maxResults <= 0 — пустой список, репозитории не трогаем")
    void invalidMaxResultsReturnsEmpty() {
        List<RecommendedEventProto> result = service.getSimilarEvents(
                SimilarEventsRequestProto.newBuilder().setEventId(1).setUserId(7).setMaxResults(0).build());

        assertThat(result).isEmpty();
        verify(eventSimilarityRepository, never()).findSimilaritiesById(anyLong());
    }

    @Test
    @DisplayName("eventId <= 0 — пустой список")
    void invalidEventIdReturnsEmpty() {
        List<RecommendedEventProto> result = service.getSimilarEvents(
                SimilarEventsRequestProto.newBuilder().setEventId(0).setUserId(7).setMaxResults(5).build());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("нет похожих событий — пустой список")
    void noSimilarEventsReturnsEmpty() {
        when(eventSimilarityRepository.findSimilaritiesById(1L)).thenReturn(List.of());

        List<RecommendedEventProto> result = service.getSimilarEvents(
                SimilarEventsRequestProto.newBuilder().setEventId(1).setUserId(7).setMaxResults(5).build());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("пары, где оба события просмотрены, отфильтровываются")
    void fullyViewedPairsAreFilteredOut() {
        when(userActionRepository.findViewedEvents(7L)).thenReturn(java.util.Set.of(1L, 2L));

        EventSimilarity pairWithBothViewed = similarity(1L, 2L);
        EventSimilarity pairWithOneNew = similarity(1L, 3L);
        List<EventSimilarity> similarities = List.of(pairWithBothViewed, pairWithOneNew);

        when(eventSimilarityRepository.findSimilaritiesById(1L))
                .thenReturn(similarities);

        List<RecommendedEventProto> result = service.getSimilarEvents(
                SimilarEventsRequestProto.newBuilder().setEventId(1).setUserId(7).setMaxResults(5).build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("сортировка по score по убыванию + limit по maxResults")
    void sortedByScoreDescAndLimited() {
        when(userActionRepository.findViewedEvents(7L)).thenReturn(java.util.Set.of());

        EventSimilarity s1 = similarity(1L, 2L, 0.5);
        EventSimilarity s2 = similarity(1L, 3L, 0.9);
        EventSimilarity s3 = similarity(1L, 4L, 0.7);
        List<EventSimilarity> similarities = List.of(s1, s2, s3);

        when(eventSimilarityRepository.findSimilaritiesById(1L))
                .thenReturn(similarities);

        List<RecommendedEventProto> result = service.getSimilarEvents(
                SimilarEventsRequestProto.newBuilder().setEventId(1).setUserId(7).setMaxResults(2).build());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventId()).isEqualTo(3L); // 0.9 — самый высокий
        assertThat(result.get(1).getEventId()).isEqualTo(4L); // 0.7 — второй
    }

    // ---------- getInteractionsCount ----------

    @Test
    @DisplayName("пустой список событий — пустой список")
    void emptyEventIdListReturnsEmpty() {
        List<RecommendedEventProto> result = service.getInteractionsCount(
                InteractionsCountRequestProto.newBuilder().build());

        assertThat(result).isEmpty();
        verify(userActionRepository, never()).getEventIdsAndRatingSums(List.of());
    }

    @Test
    @DisplayName("суммы рейтингов маппятся в прото один к одному")
    void interactionCountsAreMapped() {
        EventScoreView viewFor10 = scoreView(10L);
        when(viewFor10.getScore()).thenReturn(2.4);
        EventScoreView viewFor20 = scoreView(20L);
        when(viewFor20.getScore()).thenReturn(0.8);

        when(userActionRepository.getEventIdsAndRatingSums(List.of(10L, 20L)))
                .thenReturn(List.of(viewFor10, viewFor20));

        List<RecommendedEventProto> result = service.getInteractionsCount(
                InteractionsCountRequestProto.newBuilder()
                        .addAllEventId(List.of(10L, 20L))
                        .build());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventId()).isEqualTo(10L);
        assertThat(result.get(0).getScore()).isEqualTo(2.4, offset(1e-9));
        assertThat(result.get(1).getEventId()).isEqualTo(20L);
        assertThat(result.get(1).getScore()).isEqualTo(0.8, offset(1e-9));
    }
}