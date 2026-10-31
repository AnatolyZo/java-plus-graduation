package ru.practicum.event.service;

import ru.practicum.event.dto.event.*;
import ru.practicum.event.entity.Event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface EventService {
    List<EventDto> getEvents(long userId, int from, int size);

    EventDto createEvent(long userId, NewEventDto newEventDto);

    EventDto getEvent(long userId, long eventId);

    EventDto updateEvent(long userId, long eventId, UserUpdateEventDto userUpdateEventDto);

    Event getEventByIdAndUserId(long eventId, long userId);

    List<EventShortDto> getPublishedEvents(String text,
                                           List<Long> categories,
                                           Boolean paid,
                                           LocalDateTime rangeStart,
                                           LocalDateTime rangeEnd,
                                           boolean onlyAvailable,
                                           PublicEventSort sort,
                                           int from,
                                           int size,
                                           String ip,
                                           String uri);

    EventDto getPublishedEvent(long eventId, String ip, String uri);

    List<EventDto> searchEvents(List<Long> users,
                                List<String> states,
                                List<Long> categories,
                                String rangeStart,
                                String rangeEnd,
                                int from,
                                int size);

    EventDto updateEvent(long eventId, AdminUpdateEventDto adminUpdateEventDto);

    Set<Long> getEventsIds(List<Long> ids);

    Boolean checkExistenceWithInitiatorId(long eventId, long initiatorId);

    EventDto getEventById(long eventId);

    EventDto updateConfirmedRequests(long eventId, ConfirmedRequestsUpdate confirmedRequests);

    List<EventShortDto> getEventShortDtos(List<Long> ids);
}
