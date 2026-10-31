package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.Entities;
import ru.practicum.event.dto.event.*;
import ru.practicum.event.entity.Category;
import ru.practicum.event.entity.Event;
import ru.practicum.event.entity.LocationEmbeddable;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.mapper.LocationMapper;
import ru.practicum.event.pagination.OffsetPageRequest;
import ru.practicum.event.repository.AdminEventSearchSpecification;
import ru.practicum.event.repository.CategoryRepository;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.repository.UsersEventSearchSpecifications;
import ru.practicum.event.service.support.EventCheckService;
import ru.practicum.event.service.support.EventFactoryService;
import ru.practicum.event.service.support.StatsInteractionService;

import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EventServiceImpl extends ServiceBase implements EventService {
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final StatsInteractionService statsInteractionService;
    private final EventCheckService eventCheckService;
    private final EventFactoryService eventFactoryService;

    @Override
    public List<EventDto> getEvents(long userId, int from, int size) {
        log.trace("Инициировано получение событий пользователем с id {}", userId);
        Pageable pageable = new OffsetPageRequest(from, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable);
        log.debug("Получен список событий {}", events);
        if (events.isEmpty()) {
            return List.of();
        }

        return eventFactoryService.buildEventsWithStats(events);
    }

    @Override
    @Transactional
    public EventDto createEvent(long userId, NewEventDto newEventDto) {
        log.trace("Инициировано создание события {} пользователем с id {}", newEventDto, userId);
        Category category = findEntityIn(categoryRepository, newEventDto.getCategory(), Entities.CATEGORY);
        eventCheckService.checkUserExistence(userId);
        LocationEmbeddable locationEmbeddable = LocationMapper.toLocationEmbeddable(newEventDto.getLocation());
        //Формируем событие и сохраняем
        Event event = eventFactoryService.buildEvent(newEventDto, category, userId, locationEmbeddable);

        Event createdEvent = eventRepository.save(event);
        log.debug("Создано событие {}", createdEvent);

        EventDto result = eventFactoryService.buildEventDto(createdEvent);
        log.debug("Событие преобразовано в DTO {}", result);
        return result;
    }

    @Override
    public EventDto getEvent(long userId, long eventId) {
        log.trace("Инициировано получение события {} пользователем с id {}", eventId, userId);
        Event event = getEventByIdAndUserId(eventId, userId);
        log.debug("Получено событие {}", event);
        return eventFactoryService.buildEventWithStats(event);
    }

    @Override
    @Transactional
    public EventDto updateEvent(long userId, long eventId, UserUpdateEventDto update) {
        log.trace("Инициировано обновление события с id {} пользователем с id {}, изменения - {}", eventId, userId, update);

        Event event = getEventByIdAndUserId(eventId, userId);
        eventCheckService.checkEventNotCanceled(event);

        EventStatus status = eventFactoryService.changeEventStatus(update);
        eventFactoryService.updateEventFields(event, update, status);

        Event updatedEvent = eventRepository.save(event);
        log.debug("Обновлено событие {}", updatedEvent);
        return eventFactoryService.buildEventWithStats(updatedEvent);
    }

    @Override
    public List<EventShortDto> getPublishedEvents(String text,
                                                  List<Long> categories,
                                                  Boolean paid,
                                                  LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd,
                                                  boolean onlyAvailable,
                                                  PublicEventSort sort,
                                                  int from,
                                                  int size,
                                                  String ip,
                                                  String uri) {
        log.trace("Инициировано получение опубликованных событий");
        eventCheckService.checkDateRange(rangeStart, rangeEnd);
        statsInteractionService.saveHit(ip, uri);

        LocalDateTime start = rangeStart;
        if (rangeStart == null && rangeEnd == null) {
            start = LocalDateTime.now();
        }

        Specification<Event> specification = Specification.<Event>unrestricted()
                .and(UsersEventSearchSpecifications.hasStatus(EventStatus.PUBLISHED))
                .and(UsersEventSearchSpecifications.textContains(text))
                .and(UsersEventSearchSpecifications.categoryIn(categories))
                .and(UsersEventSearchSpecifications.paidEquals(paid))
                .and(UsersEventSearchSpecifications.eventDateFrom(start))
                .and(UsersEventSearchSpecifications.eventDateTo(rangeEnd))
                .and(UsersEventSearchSpecifications.onlyAvailable(onlyAvailable));

        if (sort == PublicEventSort.VIEWS) {
            List<Event> events = eventRepository.findAll(specification);

            if (events.isEmpty()) {
                return List.of();
            }

            List<EventShortDto> dtos = eventFactoryService.buildEventsShortWithStats(events);
            log.debug("Получен список опубликованных событий {}", dtos);
            return getPage(dtos, from, size);
        }

        Pageable pageable = new OffsetPageRequest(from, size, Sort.by("eventDate").ascending());
        Page<Event> eventPage = eventRepository.findAll(specification, pageable);
        List<Event> events = eventPage.getContent();

        if (events.isEmpty()) {
            return List.of();
        }

        List<EventShortDto> dtos = eventFactoryService.buildEventsShortWithStats(events);
        log.debug("Получен список опубликованных событий {}", dtos);
        return dtos;
    }

    @Override
    public EventDto getPublishedEvent(long eventId, String ip, String uri) {
        log.trace("Инициировано получение опубликованного события с id {}", eventId);
        Event event = eventRepository.findByIdAndStatus(eventId, EventStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(Entities.EVENT, eventId));
        statsInteractionService.saveHit(ip, uri);
        return eventFactoryService.buildEventWithStats(event);
    }

    @Override
    public Event getEventByIdAndUserId(long eventId, long userId) {
        log.trace("Инициировано получение события с id {} и инициатором с id {}", eventId, userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(Entities.EVENT, eventId));
        log.debug("Получено событие {} для запроса с eventId = {} и userId = {}", event, eventId, userId);
        return event;
    }

    @Override
    public List<EventDto> searchEvents(List<Long> users,
                                       List<String> states,
                                       List<Long> categories,
                                       String rangeStart,
                                       String rangeEnd,
                                       int from,
                                       int size) {
        log.trace("Инициирован поиск событий");
        Specification<Event> spec = new AdminEventSearchSpecification(users, states, categories, rangeStart, rangeEnd);
        Pageable pageable = new OffsetPageRequest(from, size);
        Page<Event> eventPage = eventRepository.findAll(spec, pageable);
        List<Event> events = eventPage.getContent();
        log.debug("Получен список событий {}", events);
        if (events.isEmpty()) {
            return List.of();
        }

        List<EventDto> eventDtoList = eventFactoryService.buildEventDtoList(events);
        return statsInteractionService.getEventsWithStats(eventDtoList);
    }

    @Override
    @Transactional
    public EventDto updateEvent(long eventId, AdminUpdateEventDto update) {
        log.trace("Инициировано обновление события с id {} администратором, изменения - {}", eventId, update);
        Event event = findEntityIn(eventRepository, eventId, Entities.EVENT);

        eventCheckService.checkEventNotPublished(event);
        //Валидация даты события проходит после изменения статуса для случая изменения данных о событии одновременно с публикацией
        eventCheckService.checkUpdatingEventDate(event, update);

        EventStatus status = eventFactoryService.changeEventStatus(event, update);

        eventFactoryService.updateEventFields(event, update, status);
        Event updatedEvent = eventRepository.save(event);
        log.debug("Обновлено событие {}", updatedEvent);
        return eventFactoryService.buildEventWithStats(updatedEvent);
    }

    @Override
    public Set<Long> getEventsIds(List<Long> ids) {
        log.trace("Инициировано получение id событий для внутренней проверки существования событий");
        Set<Long> eventIds = eventRepository.findEventsIds(ids);
        log.debug("Получены id событий {} для внутренней проверки", eventIds);
        return eventIds;
    }

    @Override
    public Boolean checkExistenceWithInitiatorId(long eventId, long initiatorId) {
        log.trace("Инициирована внутренняя проверка существования события по id события {} и id инициатора {}", eventId, initiatorId);
        boolean isEventExists = eventRepository.existsByIdAndInitiatorId(eventId, initiatorId);
        log.debug("Внутренняя проверка существования события по id события {} и id инициатора {} = {}", eventId, initiatorId, isEventExists);
        return isEventExists;
    }

    @Override
    public EventDto getEventById(long eventId) {
        log.trace("Инициировано получение события по id события {} для внутренних нужд", eventId);
        Event event = findEntityIn(eventRepository, eventId, Entities.EVENT);
        EventDto eventDto = EventMapper.toEventDto(event);
        eventDto.setInitiator(event.getInitiatorId());
        log.debug("Получено событие по id {} для внутренних нужд {}", eventId, eventDto);
        return eventDto;
    }

    @Override
    @Transactional
    public EventDto updateConfirmedRequests(long eventId, ConfirmedRequestsUpdate body) {
        log.trace("Инициировано внутреннее обновление подтвержденных мест {} у события с id {}", body.confirmedRequests(), eventId);
        Event event = findEntityIn(eventRepository, eventId, Entities.EVENT);
        event.setConfirmedRequests(body.confirmedRequests());
        Event updatedEvent = eventRepository.save(event);
        log.debug("Обновлены подтвержденные места {} у события с id {}", updatedEvent.getConfirmedRequests(), updatedEvent.getId());
        return EventMapper.toEventDto(updatedEvent);
    }

    @Override
    public List<EventShortDto> getEventShortDtos(List<Long> ids) {
        log.trace("Инициировано получение событий по id событий {} для внутренних нужд", ids);
        List<Event> events = eventRepository.findByIdIn(ids);

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventDto> eventWithStats = eventFactoryService.buildEventsWithStats(events);
        List<EventShortDto> eventShortDtoList = eventWithStats.stream()
                .map(EventMapper::toEventShortDto)
                .toList();
        log.debug("Получены события {} по списку id {}", eventShortDtoList, ids);
        return eventShortDtoList;
    }

    private <T> List<T> getPage(List<T> source, int from, int size) {
        if (from >= source.size()) {
            return List.of();
        }

        int toIndex = Math.min(from + size, source.size());
        return source.subList(from, toIndex);
    }
}
