package ru.practicum.event.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.event.dto.category.CategoryDto;
import ru.practicum.event.dto.event.*;
import ru.practicum.event.entity.Category;
import ru.practicum.event.entity.Event;
import ru.practicum.event.entity.LocationEmbeddable;
import ru.practicum.event.mapper.CategoryMapper;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.mapper.LocationMapper;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventFactoryService {
    private final StatsInteractionService statsInteractionService;

    public void updateEventFields(Event event, EventUpdateCommon update, EventStatus status) {
        if (update.getAnnotation() != null && !update.getAnnotation().isEmpty()) {
            event.setAnnotation(update.getAnnotation());
        }
        if (update.getDescription() != null && !update.getDescription().isEmpty()) {
            event.setDescription(update.getDescription());
        }
        if (update.getEventDate() != null) {
            event.setEventDate(update.getEventDate());
        }
        if (update.getLocation() != null) {
            LocationEmbeddable location = LocationMapper.toLocationEmbeddable(update.getLocation());
            event.setLocation(location);
        }

        if (update.getPaid() != null) {
            event.setPaid(update.getPaid());
        }

        if (update.getAnnotation() != null && !update.getAnnotation().isEmpty()) {
            event.setAnnotation(update.getAnnotation());
        }

        if (update.getParticipantLimit() != null) {
            event.setParticipantLimit(update.getParticipantLimit());
        }

        if (update.getRequestModeration() != null) {
            event.setRequestModeration(update.getRequestModeration());
        }

        event.setStatus(status);

        if (update.getTitle() != null && !update.getTitle().isEmpty()) {
            event.setTitle(update.getTitle());
        }
    }

    private void setParamsOnCreation(Event event, NewEventDto newEventDto) {
        event.setCreatedOn(LocalDateTime.now());
        event.setStatus(EventStatus.PENDING);
        event.setRequestModeration(newEventDto.getRequestModerationOrDefault());
    }

    public EventStatus changeEventStatus(UserUpdateEventDto update) {
        EventStatus status;

        if (update.getStatus() != null && update.getStatus().equals(UserEventUpdateAction.CANCEL_REVIEW)) {
            status = EventStatus.CANCELED;
        } else {
            status = EventStatus.PENDING;
        }

        return status;
    }

    public EventStatus changeEventStatus(Event event, AdminUpdateEventDto update) {
        EventStatus status;

        if (update.getStatus() != null && update.getStatus().equals(AdminEventUpdateAction.PUBLISH_EVENT)) {
            status = EventStatus.PUBLISHED;
            event.setPublishedOn(LocalDateTime.now());
        } else if (update.getStatus() != null && update.getStatus().equals(AdminEventUpdateAction.REJECT_EVENT)) {
            status = EventStatus.CANCELED;
        } else {
            status = event.getStatus();
        }

        return status;
    }

    private void setNestedClassesValues(Event event,
                                        Category category,
                                        long initiatorId,
                                        LocationEmbeddable locationEmbeddable) {
        event.setCategory(category);
        event.setInitiatorId(initiatorId);
        event.setLocation(locationEmbeddable);
    }

    private void setNestedClassesValues(EventDto event,
                                        CategoryDto category,
                                        long initiatorId,
                                        Location location) {
        event.setCategory(category);
        event.setInitiator(initiatorId);
        event.setLocation(location);
    }

    public List<EventDto> buildEventDtoList(List<Event> events) {
        return events.stream()
                .map(event -> {
                    CategoryDto categoryDto = CategoryMapper.toCategoryDto(event.getCategory());
                    long initiatorId = event.getInitiatorId();
                    Location location = LocationMapper.toLocation(event.getLocation());
                    EventDto eventDto = EventMapper.toEventDto(event);
                    setNestedClassesValues(eventDto, categoryDto, initiatorId, location);
                    return eventDto;
                })
                .toList();
    }

    public Event buildEvent(NewEventDto newEventDto,
                            Category category,
                            long userId,
                            LocationEmbeddable locationEmbeddable) {
        Event event = EventMapper.toEvent(newEventDto);
        setNestedClassesValues(event, category, userId, locationEmbeddable);
        setParamsOnCreation(event, newEventDto);
        return event;
    }

    public EventDto buildEventDto(Event createdEvent) {
        EventDto eventDto = EventMapper.toEventDto(createdEvent);
        CategoryDto categoryDto = CategoryMapper.toCategoryDto(createdEvent.getCategory());
        Location location = LocationMapper.toLocation(createdEvent.getLocation());
        setNestedClassesValues(eventDto, categoryDto, eventDto.getInitiator(), location);
        return eventDto;
    }

    public EventDto buildEventWithStats(Event events) {
        List<EventDto> eventDtoList = buildEventDtoList(List.of(events));
        return statsInteractionService.getEventsWithStats(eventDtoList).getFirst();
    }

    public List<EventDto> buildEventsWithStats(List<Event> events) {
        List<EventDto> eventDtoList = buildEventDtoList(events);
        return statsInteractionService.getEventsWithStats(eventDtoList);
    }

    public List<EventShortDto> buildEventsShortWithStats(List<Event> events) {
        List<EventDto> eventDtoList = buildEventDtoList(events);
        return statsInteractionService.getEventsWithStats(eventDtoList).stream()
                .map(EventMapper::toEventShortDto)
                .sorted(Comparator.comparingLong(EventShortDto::getViews).reversed())
                .toList();
    }
}