package ru.practicum.analyzer.service;

import ru.practicum.grpc.stats.service.dashboard.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.service.dashboard.RecommendedEventProto;
import ru.practicum.grpc.stats.service.dashboard.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.service.dashboard.UserPredictionsRequestProto;

import java.util.List;

public interface RecommendationService {
    List<RecommendedEventProto> getRecommendationsForUser(UserPredictionsRequestProto request);

    List<RecommendedEventProto> getSimilarEvents(SimilarEventsRequestProto request);

    List<RecommendedEventProto> getInteractionsCount(InteractionsCountRequestProto request);
}
