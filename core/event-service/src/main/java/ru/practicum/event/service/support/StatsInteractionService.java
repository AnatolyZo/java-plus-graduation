package ru.practicum.event.service.support;

import ru.practicum.event.dto.event.EventDto;

import java.util.List;

public interface StatsInteractionService {
    List<EventDto> getEventsWithStats(List<EventDto> events);

    void saveHit(String ip, String uri);
}
