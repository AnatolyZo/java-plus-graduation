package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.request.dto.RequestStatus;
import ru.practicum.request.entity.Request;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {
    List<Request> findByEventId(long eventId);

    List<Request> findByIdIn(List<Long> requestIds);

    List<Request> findByRequesterIdOrderByCreatedDesc(Long requesterId);

    boolean existsByEventIdAndRequesterId(long eventId, long userId);

    boolean existsByEventIdAndRequesterIdAndStatus(long eventId, long requesterId, RequestStatus status);
}