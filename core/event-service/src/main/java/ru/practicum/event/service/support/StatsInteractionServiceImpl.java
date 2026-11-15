package ru.practicum.event.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.explorewithme.AnalyzerClient;
import ru.practicum.explorewithme.CollectorClient;
import ru.practicum.grpc.stats.service.dashboard.RecommendedEventProto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsInteractionServiceImpl implements StatsInteractionService {
    private final AnalyzerClient analyzerClient;
    private final CollectorClient collectorClient;
    private static final int AMOUNT_OF_RECOMMENDATIONS = 20;

    @Override
    public Map<Long, Double> getStats(List<Long> eventIds) {
        Stream<RecommendedEventProto> stats = analyzerClient.getInteractionsCount(eventIds);
        return buildRatingsMap(stats);
    }

    @Override
    public Map<Long, Double> getRecommendations(long userId) {
        Stream<RecommendedEventProto> stats = analyzerClient.getRecommendationsForUser(userId, AMOUNT_OF_RECOMMENDATIONS);
        return buildRatingsMap(stats);
    }

    @Override
    public void addView(long userId, long eventId) {
        collectorClient.addView(userId, eventId);
    }

    @Override
    public void addLike(long userId, long eventId) {
        collectorClient.addLike(userId, eventId);
    }

    private Map<Long, Double> buildRatingsMap(Stream<RecommendedEventProto> stats) {
        Map<Long, Double> ratingByEventId = new HashMap<>();
        stats.forEach(stat -> ratingByEventId.put(stat.getEventId(), stat.getScore()));
        return ratingByEventId;
    }
}
