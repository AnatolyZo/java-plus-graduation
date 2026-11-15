package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.analyzer.entity.EventScoreView;
import ru.practicum.analyzer.entity.EventSimilarity;
import ru.practicum.analyzer.entity.NeighborView;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.analyzer.repository.UserActionRepository;
import ru.practicum.grpc.stats.service.dashboard.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.service.dashboard.RecommendedEventProto;
import ru.practicum.grpc.stats.service.dashboard.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.service.dashboard.UserPredictionsRequestProto;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {
    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;
    private static final int K_NEAREST_NEIGHBORS = 5;

    @Override
    public List<RecommendedEventProto> getRecommendationsForUser(UserPredictionsRequestProto request) {
        log.trace("Начат процесс получения рекомендаций");
        List<Long> viewedEventIds = userActionRepository.findViewedActionsOrderedByTimestamp(request.getUserId(), request.getMaxResults());
        log.debug("Получены недавно просмотренные пользователем с id {} мероприятия {}", request.getUserId(), viewedEventIds);

        if (viewedEventIds.isEmpty()) {
            return List.of();
        }

        List<EventScoreView> similarities = eventSimilarityRepository.findSimilarNotInteracted(viewedEventIds, request.getUserId(), request.getMaxResults());
        log.debug("Для пользователя с id {} получены похожие мероприятия, с которыми он не взаимодействавал, similarities = {}", request.getUserId(), similarities);

        if (similarities.isEmpty()) {
            return List.of();
        }

        Map<Long, List<NeighborView>> neighbors = getNeighbors(similarities, request.getUserId());
        log.debug("Для похожих мероприятий пользователю с id {} найдены по K соседей по подобию {}", request.getUserId(), neighbors);

        List<RecommendedEventProto> recommendations = buildRecommendations(similarities, neighbors);
        log.debug("Для пользователя с id {} сформирован список рекомендаций {}", request.getUserId(), recommendations);
        return recommendations;
    }

    @Override
    public List<RecommendedEventProto> getSimilarEvents(SimilarEventsRequestProto request) {
        log.trace("Начат процесс поиска похожих событий");
        long eventId = request.getEventId();

        if (request.getMaxResults() <= 0 || eventId <= 0) {
            return List.of();
        }

        List<EventSimilarity> similarities = eventSimilarityRepository.findSimilaritiesById(eventId);
        log.debug("Для мерприятия с id {} найден список похожих событий {}", eventId, similarities);

        if (similarities.isEmpty()) {
            return List.of();
        }

        Set<Long> viewedEventIds = userActionRepository.findViewedEvents(request.getUserId());
        log.debug("Для пользователя с id {} найден список просмотренных мероприятий {}", request.getUserId(), viewedEventIds);

        List<RecommendedEventProto> similaritiesList = buildSimilitiesList(request, similarities, viewedEventIds);
        log.debug("Получен список похожих событий с исключением уже просмотренных {}", similaritiesList);
        return similaritiesList;
    }

    @Override
    public List<RecommendedEventProto> getInteractionsCount(InteractionsCountRequestProto request) {
        log.trace("Начат процесс полуения суммы максимальных весов действий для мероприятий");
        if (request.getEventIdList().isEmpty()) {
            return Collections.emptyList();
        }

        List<RecommendedEventProto> interactions = buildInteractionsList(request);
        log.debug("Для списка id = {} сформирован список сумм максимальных весов действий {}", request.getEventIdList(), interactions);
        return interactions;
    }

    private Map<Long, List<NeighborView>> getNeighbors(List<EventScoreView> similarities, long userId) {
        return similarities.stream()
                .map(EventScoreView::getEventId)
                .collect(Collectors.toMap(
                        Function.identity(),
                        eventId -> eventSimilarityRepository.findNearestNeighbors(eventId, userId, K_NEAREST_NEIGHBORS)
                ));
    }

    private double computeScore(EventScoreView similarity, Map<Long, List<NeighborView>> neighborsMap) {
        List<NeighborView> neighbors = neighborsMap.getOrDefault(similarity.getEventId(), List.of());

        double sumOfSimilarityCoefficients = neighbors.stream()
                .mapToDouble(NeighborView::getSimilarity)
                .sum();

        if (sumOfSimilarityCoefficients == 0.0) {
            return 0.0;
        }

        double sumOfWeightedGrades = neighbors.stream()
                .mapToDouble(neighbor -> neighbor.getRating() * neighbor.getSimilarity())
                .sum();

        return sumOfWeightedGrades / sumOfSimilarityCoefficients;
    }

    private RecommendedEventProto buildRecommendedEventProto(long eventId, double score) {
        return RecommendedEventProto.newBuilder()
                .setEventId(eventId)
                .setScore(score)
                .build();
    }

    private List<RecommendedEventProto> buildRecommendations(List<EventScoreView> similarities, Map<Long, List<NeighborView>> neighbors) {
        return similarities.stream()
                .map(similarity -> {
                    double predictedScore = computeScore(similarity, neighbors);
                    return buildRecommendedEventProto(similarity.getEventId(), predictedScore);
                })
                .toList();
    }

    private List<RecommendedEventProto> buildSimilitiesList(SimilarEventsRequestProto request, List<EventSimilarity> similarities, Set<Long> viewedEventIds) {
        return similarities.stream()
                .filter(similarity -> !(viewedEventIds.contains(similarity.getEventA()) && viewedEventIds.contains(similarity.getEventB())))
                .sorted(Comparator.comparing(EventSimilarity::getScore).reversed())
                .limit(request.getMaxResults())
                .map(similarity -> {
                    long similarId = similarity.getEventA() == request.getEventId()
                            ? similarity.getEventB()
                            : similarity.getEventA();

                    return buildRecommendedEventProto(similarId, similarity.getScore());
                })
                .toList();
    }

    private List<RecommendedEventProto> buildInteractionsList(InteractionsCountRequestProto request) {
        return userActionRepository.getEventIdsAndRatingSums(request.getEventIdList()).stream()
                .map(eventScoreView -> buildRecommendedEventProto(eventScoreView.getEventId(), eventScoreView.getScore()))
                .toList();
    }
}