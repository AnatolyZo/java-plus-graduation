package ru.practicum.event.service.support;

import java.util.List;
import java.util.Map;

public interface StatsInteractionService {
    Map<Long, Double> getStats(List<Long> eventIds);

    Map<Long, Double> getRecommendations(long userId);

    void addView(long userId, long eventId);

    void addLike(long userId, long eventId);
}
