package ru.practicum.aggregator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.aggregator.model.EventPair;
import ru.practicum.aggregator.model.WeightData;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class EventSimilarityComputerImpl implements EventSimilarityComputer {
    //Отображение usersRatedEvents (id пользователя, множество id оцененных мероприятий этим пользователем)
    //необходимо для удобства пересчета косинусного сходства мероприятий,
    //так как вне зависимости от ситуации (новое событие или обновление оценки старого)
    //пересчет имеет смысл только для мероприятий, имеющих оценку этого пользователя
    private final Map<Long, Set<Long>> usersRatedEvents = new ConcurrentHashMap<>();
    private final Map<Long, ConcurrentHashMap<Long, Double>> userWeightsByEvents = new ConcurrentHashMap<>();
    private final Map<Long, Double> eventTotalWeights = new ConcurrentHashMap<>();
    private final Map<EventPair, Double> eventPairsMinWeightSums = new ConcurrentHashMap<>();
    private final Map<ActionTypeAvro, Double> actionWeights = Map.of(
            ActionTypeAvro.VIEW, 0.4,
            ActionTypeAvro.REGISTER, 0.8,
            ActionTypeAvro.LIKE, 1.0
    );
    private static final int AMOUNT_OF_EVENTS_TO_COMPUTE_SIMILARITY = 2;

    @Override
    public List<EventSimilarityAvro> compute(UserActionAvro action) {
        log.trace("Начат расчет косинусного сходства с событием {}", action);
        if (action.getActionType() == null || actionWeights.get(action.getActionType()) == null) {
            return List.of();
        }

        Set<Long> usersEvents = addEventAndGetUserEvents(action);

        //WeightData - класс для удобства хранения старого веса события, нового веса и их разности
        WeightData weightData = incrementEventWeight(action);

        //Если новое действие пользователя не изменило вес, то это означает,
        //что сумма весов мероприятия не изменилась, сумма минимальных весов с другими мероприятиями
        //также не изменилась, поэтому дальнейший пересчет не требуется
        if (weightData.delta() == 0) {
            return List.of();
        }

        //Расчеты сходства событий будут производиться при оценке 2 и более событий пользователем
        if (usersEvents.size() < AMOUNT_OF_EVENTS_TO_COMPUTE_SIMILARITY) {
            return List.of();
        }

        List<EventSimilarityAvro> eventSimilarities = computeSimilaritiesForEvent(action, usersEvents, weightData);
        log.debug("Получен список обновлений косинусного сходства мероприятий {}", eventSimilarities);
        return eventSimilarities;
    }

    private Set<Long> addEventAndGetUserEvents(UserActionAvro action) {
        Set<Long> usersEvents = usersRatedEvents.computeIfAbsent(action.getUserId(), v -> ConcurrentHashMap.newKeySet());
        usersEvents.add(action.getEventId());
        log.debug("Обновлено множество оцененных событий {} пользователем с id {}", usersEvents, action.getUserId());
        return usersEvents;
    }

    private WeightData incrementEventWeight(UserActionAvro action) {
        WeightData weightData = computeWeightData(action);

        if (weightData.delta() != 0) {
            eventTotalWeights.merge(action.getEventId(), weightData.delta(), Double::sum);
        }

        log.debug("Сформирована информация по изменению веса собития weightData = {}", weightData);
        return weightData;
    }

    private WeightData computeWeightData(UserActionAvro action) {
        Double actionWeight = actionWeights.get(action.getActionType());
        double[] usersOldWeight = {0d};

        //Присвоение usersOldWeight[0] в лямбде необходимо для устранения гонки данных,
        //которая бы могла возникнуть при отдельном получении usersOldWeight через userWeightsByEvents.get(action.getEventId()).get(action.get.userId())
        double usersNewWeight = userWeightsByEvents
                .computeIfAbsent(action.getEventId(), v -> new ConcurrentHashMap<>())
                .merge(
                        action.getUserId(),
                        actionWeight,
                        (oldWeight, newWeight) -> {
                            usersOldWeight[0] = oldWeight;
                            return Math.max(oldWeight, newWeight);
                        });

        return new WeightData(usersNewWeight - usersOldWeight[0], usersNewWeight, usersOldWeight[0]);
    }

    private List<EventSimilarityAvro> computeSimilaritiesForEvent(UserActionAvro action, Set<Long> usersEvents, WeightData weightData) {
        return usersEvents.stream()
                .filter(otherEventId -> otherEventId != action.getEventId()) //исключаем id, полученный в запросе, чтобы не считать сходство с самим собой
                .map(otherEventId -> EventPair.of(action.getEventId(), otherEventId)) //формируем все возможные пары с id, полученным в запросе
                .map(pair -> {
                    incrementPairMinWeightsSum(pair, action, weightData);
                    double score = computePairScore(pair);
                    log.debug("Косинусное сходство пары мероприятий {} составило score = {}", pair, score);
                    return buildEventSimilarityAvro(pair, score);
                })
                .toList();
    }

    private void incrementPairMinWeightsSum(EventPair pair, UserActionAvro action, WeightData weightData) {
        double delta = computeDeltaMinWeightSum(pair, action, weightData);

        if (delta != 0d) {
            eventPairsMinWeightSums.merge(pair, delta, Double::sum);
        }

        log.debug("Приращение суммы минимальных весов пары мероприятий {} составило delta = {}", pair, delta);
    }

    private double computeDeltaMinWeightSum(EventPair pair, UserActionAvro action, WeightData weightData) {
        long otherEventId = pair.getOppositeIdTo(action.getEventId());
        double otherEventWeight = userWeightsByEvents.get(otherEventId).getOrDefault(action.getUserId(), 0d);
        return Math.min(weightData.newWeight(), otherEventWeight) - Math.min(weightData.oldWeight(), otherEventWeight);
    }

    private double computePairScore(EventPair pair) {
        double minWeightsSum = eventPairsMinWeightSums.getOrDefault(pair, 0d);
        double eventATotalWeight = eventTotalWeights.getOrDefault(pair.eventA(), 0d);
        double eventBTotalWeight = eventTotalWeights.getOrDefault(pair.eventB(), 0d);

        if (eventATotalWeight == 0d || eventBTotalWeight == 0d) {
            return 0d;
        }

        return minWeightsSum / Math.sqrt(eventATotalWeight * eventBTotalWeight);
    }

    private EventSimilarityAvro buildEventSimilarityAvro(EventPair eventPair, double score) {
        return EventSimilarityAvro.newBuilder()
                .setEventA(eventPair.eventA())
                .setEventB(eventPair.eventB())
                .setScore(score)
                .setTimestamp(Instant.now())
                .build();
    }
}
