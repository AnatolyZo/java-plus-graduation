package ru.practicum.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.event.dto.event.EventStatus;
import ru.practicum.event.entity.Event;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    boolean existsByCategoryId(long categoryId);

    boolean existsByIdAndInitiatorId(long eventId, long initiatorId);

    List<Event> findByInitiatorId(long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiatorId(long eventId, long initiatorId);

    Optional<Event> findById(Long eventId);

    Optional<Event> findByIdAndStatus(long eventId, EventStatus status);

    List<Event> findByIdIn(List<Long> eventIds);

    @Query(value = "SELECT e.id FROM Event e WHERE e.id IN :ids")
    Set<Long> findEventsIds(List<Long> ids);
}
