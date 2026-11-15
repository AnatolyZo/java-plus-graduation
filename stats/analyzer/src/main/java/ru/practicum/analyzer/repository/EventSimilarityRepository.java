package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.entity.EventScoreView;
import ru.practicum.analyzer.entity.EventSimilarity;
import ru.practicum.analyzer.entity.EventSimilarityId;
import ru.practicum.analyzer.entity.NeighborView;

import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, EventSimilarityId> {
    @Query(value = """
            SELECT *
            FROM event_similarity es
            WHERE :eventId IN (es.event_a, es.event_b)
            """, nativeQuery = true)
    List<EventSimilarity> findSimilaritiesById(@Param("eventId") long eventId);

    @Query(value = """
            SELECT CASE WHEN es.event_a IN (:eventIds) THEN es.event_b ELSE es.event_a END AS event_id,
                               es.score AS score
            FROM event_similarity es
            WHERE (es.event_a IN (:eventIds) OR es.event_b IN (:eventIds))
                  AND NOT EXISTS (
                        SELECT 1
                        FROM user_actions ua
                        WHERE ua.user_id = :userId
                          AND ua.event_id = CASE WHEN es.event_a IN (:eventIds)
                                                 THEN es.event_b
                                                 ELSE es.event_a
                                            END
                  )
            ORDER BY score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<EventScoreView> findSimilarNotInteracted(@Param("eventIds") List<Long> eventIds,
                                                  @Param("userId") long userId,
                                                  @Param("limit") int limit);

    @Query(value = """
        SELECT u.event_id AS event_id,
               u.rating    AS rating,
               nn.score   AS similarity
        FROM (
            SELECT CASE WHEN es.event_a = :eventId THEN es.event_b ELSE es.event_a END AS neighbor_id,
                   es.score AS score
            FROM event_similarity es
            WHERE (es.event_a = :eventId OR es.event_b = :eventId)
              AND EXISTS (
                    SELECT 1
                    FROM user_actions ua
                    WHERE ua.user_id = :userId
                      AND ua.event_id = CASE WHEN es.event_a = :eventId
                                             THEN es.event_b
                                             ELSE es.event_a
                                        END
                  )
            ORDER BY es.score DESC
            LIMIT :kNearestNeighbors
        ) nn
        JOIN user_actions u
          ON u.event_id = nn.neighbor_id
         AND u.user_id = :userId
        """, nativeQuery = true)
    List<NeighborView> findNearestNeighbors(@Param("eventId") long eventId,
                                            @Param("userId") long userId,
                                            @Param("kNearestNeighbors") int kNearestNeighbors);
}
