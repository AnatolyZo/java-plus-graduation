package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.entity.EventScoreView;
import ru.practicum.analyzer.entity.UserAction;
import ru.practicum.analyzer.entity.UserActionId;

import java.util.List;
import java.util.Set;

public interface UserActionRepository extends JpaRepository<UserAction, UserActionId> {
    @Query(value = """
            SELECT u.event_id
            FROM user_actions u
            WHERE u.user_id = :userId
            ORDER BY u.timestamp DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findViewedActionsOrderedByTimestamp(@Param("userId") long userId, @Param("limit") int limit);

    @Query(value = """
            SELECT u.event_id
            FROM user_actions u
            WHERE u.user_id = :userId
            """, nativeQuery = true)
    Set<Long> findViewedEvents(@Param("userId") long userId);

    @Query(value = """
            SELECT u.event_id AS event_id,
                   SUM(u.rating) AS score
            FROM user_actions u
            WHERE u.event_id IN :eventIds
            GROUP BY u.event_id
            ORDER BY score DESC
            """, nativeQuery = true)
    List<EventScoreView> getEventIdsAndRatingSums(@Param("eventIds") List<Long> eventIds);
}
