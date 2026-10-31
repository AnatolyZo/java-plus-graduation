package ru.practicum.event;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.common.Entities;
import ru.practicum.event.dto.category.CategoryDto;
import ru.practicum.event.dto.event.*;
import ru.practicum.event.entity.Category;
import ru.practicum.event.entity.Event;
import ru.practicum.event.entity.LocationEmbeddable;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.exception.UnavailableUpdateException;
import ru.practicum.event.repository.AdminEventSearchSpecification;
import ru.practicum.event.repository.CategoryRepository;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.EventServiceImpl;
import ru.practicum.event.service.support.EventCheckService;
import ru.practicum.event.service.support.EventFactoryService;
import ru.practicum.event.service.support.StatsInteractionService;
import ru.practicum.explorewithme.StatsClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class EventServiceImplTests {

    @InjectMocks
    private EventServiceImpl eventService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventCheckService eventCheckService;

    @Mock
    private StatsInteractionService statsInteractionService;

    @Mock
    private EventFactoryService eventFactoryService;

    @Mock
    private StatsClient statsClient;

    @Mock
    private AdminEventSearchSpecification spec;

    private static final long USER_ID = 1L;
    private static final long EVENT_ID = 1L;
    private static final long CATEGORY_ID = 1L;
    private static final String IP = "127.0.0.1";
    private static final String EVENTS_URI = "/events";

    private NewEventDto newEventDto;
    private UserUpdateEventDto userUpdateEventDto;

    @BeforeEach
    void setUp() {
        Location location = new Location(1.0, 1.0);
        newEventDto = NewEventDto.builder()
                .title("Event Title")
                .description("Description")
                .annotation("Annotation")
                .category(1L)
                .eventDate(LocalDateTime.now().plusHours(3))
                .location(location)
                .paid(false)
                .participantLimit(10)
                .requestModeration(true)
                .build();

        userUpdateEventDto = UserUpdateEventDto.builder()
                .title("Updated Title")
                .status(UserEventUpdateAction.SEND_TO_REVIEW)
                .build();
    }


    @Test
    void getEvents_returnsListWithStats() {
        int from = 0;
        int size = 5;

        Event event = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PENDING);

        List<Event> events = List.of(event);

        EventDto eventDto = EventDto.builder()
                .id(EVENT_ID)
                .views(7L)
                .build();
        when(eventFactoryService.buildEventsWithStats(events)).thenReturn(List.of(eventDto));
        when(eventRepository.findByInitiatorId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(events);

        List<EventDto> result = eventService.getEvents(USER_ID, from, size);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(event.getId());
        assertThat(result.get(0).getViews()).isEqualTo(7L);

        verify(eventRepository).findByInitiatorId(eq(USER_ID), any(Pageable.class));
        verify(eventFactoryService).buildEventsWithStats(eq(events));
    }

    @Test
    void createEvent_createsEventWithCorrectFields() {
        Event formedEvent = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PENDING);
        EventDto expectedEvent = EventDto.builder()
                .id(EVENT_ID)
                .status(EventStatus.PENDING)
                .build();
        Category category = createTestCategory(CATEGORY_ID);

        when(categoryRepository.findById(eq(newEventDto.getCategory()))).thenReturn(Optional.of(category));

        doNothing().when(eventCheckService).checkUserExistence(eq(USER_ID));

        when(eventFactoryService.buildEvent(
                eq(newEventDto),
                eq(category),
                eq(USER_ID),
                any()
        )).thenReturn(formedEvent);
        when(eventFactoryService.buildEventDto(eq(formedEvent))).thenReturn(expectedEvent);

        when(eventRepository.save(eq(formedEvent))).thenReturn(formedEvent);

        EventDto result = eventService.createEvent(USER_ID, newEventDto);

        assertThat(result.getId()).isEqualTo(expectedEvent.getId());
        assertThat(result.getStatus()).isEqualTo(EventStatus.PENDING);

        verify(categoryRepository).findById(eq(newEventDto.getCategory()));
        verify(eventCheckService).checkUserExistence(eq(USER_ID));
        verify(eventRepository).save(eq(formedEvent));
    }

    @Test
    void getEvent_returnsEventDtoWithStats() {
        Event event = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PENDING);

        EventDto baseDto = EventDto.builder().id(EVENT_ID).views(5L).build();
        when(eventFactoryService.buildEventWithStats(event)).thenReturn(baseDto);

        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.of(event));

        EventDto result = eventService.getEvent(USER_ID, EVENT_ID);

        assertThat(result.getId()).isEqualTo(EVENT_ID);
        assertThat(result.getViews()).isEqualTo(5L);

        verify(eventRepository).findByIdAndInitiatorId(eq(EVENT_ID), eq(USER_ID));
        verify(eventFactoryService).buildEventWithStats(eq(event));
    }

    @Test
    void getEvent_throwsNotFoundException_whenEventDoesNotBelongToUser() {
        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eventService.getEvent(USER_ID, EVENT_ID));
    }

    @Test
    void updateEvent_updatesEventFields_whenStatusAllows() {
        Event existingEvent = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PENDING);
        UserUpdateEventDto update = createTestUpdateEventDto(UserEventUpdateAction.SEND_TO_REVIEW);

        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existingEvent));
        when(eventRepository.save(any(Event.class))).thenReturn(existingEvent);

        existingEvent.setTitle("Updated Event Title");
        existingEvent.setDescription("Updated detailed description that satisfies the 20–7000 character size constraint.");
        existingEvent.setPaid(true);

        EventDto baseDto = EventDto.builder()
                .id(existingEvent.getId())
                .title(existingEvent.getTitle())
                .description(existingEvent.getDescription())
                .paid(existingEvent.isPaid())
                .status(existingEvent.getStatus())
                .views(3L)
                .build();
        when(eventFactoryService.buildEventWithStats(eq(existingEvent))).thenReturn(baseDto);

        EventDto result = eventService.updateEvent(USER_ID, EVENT_ID, update);

        assertThat(result.getTitle()).isEqualTo("Updated Event Title");
        assertThat(result.getDescription()).isEqualTo("Updated detailed description that satisfies the 20–7000 character size constraint.");
        assertThat(result.isPaid()).isEqualTo(true);
        assertThat(result.getViews()).isEqualTo(3L);

        verify(eventRepository).save(existingEvent);
    }

    @Test
    void updateEvent_throwsUnavailableUpdateException_whenStatusDoesNotAllowUpdate() {
        Event existingEvent = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PUBLISHED);
        UserUpdateEventDto update = UserUpdateEventDto.builder().build();

        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existingEvent));

        doThrow(new UnavailableUpdateException(Entities.EVENT.name(), EVENT_ID))
                .when(eventCheckService).checkEventNotCanceled(eq(existingEvent));

        assertThrows(UnavailableUpdateException.class,
                () -> eventService.updateEvent(USER_ID, EVENT_ID, update));
    }

    @Test
    void updateEvent_cancelsEvent_whenStatusActionIsCancel() {
        Event existingEvent = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PENDING);
        UserUpdateEventDto update = createTestUpdateEventDto(UserEventUpdateAction.CANCEL_REVIEW);

        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existingEvent));
        doNothing().when(eventCheckService).checkEventNotCanceled(eq(existingEvent));

        doAnswer(inv -> {
            Event e = inv.getArgument(0);
            e.setStatus(EventStatus.CANCELED);
            return null;
        }).when(eventFactoryService).updateEventFields(any(), any(), any());

        Event updatedEvent = mock(Event.class);
        when(eventRepository.save(any())).thenReturn(updatedEvent);

        EventDto baseDto = EventDto.builder().id(EVENT_ID).status(EventStatus.CANCELED).build();
        when(eventFactoryService.buildEventWithStats(updatedEvent)).thenReturn(baseDto);

        EventDto result = eventService.updateEvent(USER_ID, EVENT_ID, update);

        assertThat(existingEvent.getStatus()).isEqualTo(EventStatus.CANCELED);
        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELED);
    }

    @Test
    void searchEvents_filtersAndPagination_returnsCorrectPage() {
        List<Long> users = List.of(1L, 2L);
        List<String> states = List.of("PUBLISHED", "CANCELLED");
        List<Long> categories = List.of(3L);
        String rangeStart = "2024-01-01 00:00:00";
        String rangeEnd = "2024-12-31 23:59:59";
        int from = 5;
        int size = 10;

        Event e1 = createTestEvent(1L, 1L, 3L, EventStatus.PUBLISHED);
        Event e2 = createTestEvent(2L, 2L, 3L, EventStatus.CANCELED);
        List<Event> pageContent = List.of(e1, e2);

        Page<Event> page = new PageImpl<>(pageContent, PageRequest.of(0, size), 25L);

        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        EventDto dto1 = EventDto.builder().id(1L).build();
        EventDto dto2 = EventDto.builder().id(2L).build();
        List<EventDto> dtoList = List.of(dto1, dto2);
        when(eventFactoryService.buildEventDtoList(pageContent)).thenReturn(dtoList);

        EventDto res1 = EventDto.builder().id(1L).views(10L).build();
        EventDto res2 = EventDto.builder().id(2L).views(20L).build();
        when(statsInteractionService.getEventsWithStats(dtoList)).thenReturn(List.of(res1, res2));

        List<EventDto> result = eventService.searchEvents(users, states, categories, rangeStart, rangeEnd, from, size);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(0).getViews()).isEqualTo(10L);
        assertThat(result.get(1).getViews()).isEqualTo(20L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getOffset()).isEqualTo(from);
        assertThat(captured.getPageSize()).isEqualTo(size);
    }

    @Test
    void searchEvents_emptyResult_returnsEmptyList() {
        List<Long> users = Collections.emptyList();
        List<String> states = Collections.emptyList();
        List<Long> categories = Collections.emptyList();
        String rangeStart = null;
        String rangeEnd = null;
        int from = 0;
        int size = 5;

        Page<Event> emptyPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        List<EventDto> result = eventService.searchEvents(users, states, categories, rangeStart, rangeEnd, from, size);

        assertThat(result).isEmpty();
    }

    @Test
    void updateEventByAdmin_validPendingEvent_successfullyUpdatesAndReturnsDto() {
        long eventId = 1L;
        long initiatorId = 2L;

        Event event = createTestEvent(eventId, initiatorId, 3L, EventStatus.PENDING);
        event.setParticipantLimit(10);
        event.setConfirmedRequests(2);

        AdminUpdateEventDto body = createUpdateEventDto(AdminEventUpdateAction.PUBLISH_EVENT);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        event.setStatus(EventStatus.PUBLISHED);

        EventDto baseDto = EventDto.builder()
                .id(event.getId())
                .status(event.getStatus())
                .participantLimit(event.getParticipantLimit())
                .confirmedRequests(event.getConfirmedRequests())
                .views(42L)
                .build();
        when(eventFactoryService.buildEventWithStats(eq(event))).thenReturn(baseDto);

        EventDto result = eventService.updateEvent(eventId, body);

        assertThat(result.getId()).isEqualTo(eventId);
        assertThat(result.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(result.getViews()).isEqualTo(42L);

        verify(eventRepository).save(event);
        verifyNoMoreInteractions(eventRepository);
    }

    @Test
    void updateEventByAdmin_eventNotFound_throwsNotFoundException() {
        long eventId = 999L;
        AdminUpdateEventDto body = createUpdateEventDto(AdminEventUpdateAction.PUBLISH_EVENT);

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.updateEvent(eventId, body))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateEventByAdmin_nonPendingEvent_throwsUnavailableUpdateException() {
        Event event = createTestEvent(EVENT_ID, USER_ID, CATEGORY_ID, EventStatus.PUBLISHED);
        AdminUpdateEventDto body = createUpdateEventDto(AdminEventUpdateAction.REJECT_EVENT);

        doThrow(new UnavailableUpdateException(Entities.EVENT.name(), EVENT_ID))
                .when(eventCheckService)
                .checkEventNotPublished(any(Event.class));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.updateEvent(EVENT_ID, body))
                .isInstanceOf(UnavailableUpdateException.class)
                .hasMessageContaining(Entities.EVENT.name());

        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEvent_rejectAction_mapsToCancelledStatus() {
        long eventId = 1L;
        Event event = createTestEvent(eventId, 1L, 3L, EventStatus.PENDING);
        AdminUpdateEventDto body = createUpdateEventDto(AdminEventUpdateAction.REJECT_EVENT);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        event.setStatus(EventStatus.CANCELED);

        EventDto baseDto = EventDto.builder()
                .id(event.getId())
                .status(event.getStatus())
                .build();
        when(eventFactoryService.buildEventWithStats(eq(event))).thenReturn(baseDto);

        EventDto result = eventService.updateEvent(eventId, body);

        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELED);
    }

    @Test
    void getPublishedEvents_returnsEventsWithViewsFromStatsMap() {
        Event first = createEvent(1L, "First event", EventStatus.PUBLISHED, 0);
        Event second = createEvent(2L, "Second event", EventStatus.PUBLISHED, 0);
        List<Event> events = List.of(first, second);

        doNothing().when(eventCheckService).checkDateRange(any(), any());
        doNothing().when(statsInteractionService).saveHit(any(), any());
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(events));

        EventShortDto dto1 = EventShortDto.builder()
                .id(1L)
                .views(11L)
                .build();


        EventShortDto dto2 = EventShortDto.builder()
                .id(2L)
                .views(22L)
                .build();

        List<EventShortDto> expectedDtos = List.of(dto1, dto2);

        when(eventFactoryService.buildEventsShortWithStats(any()))
                .thenReturn(expectedDtos);

        List<EventShortDto> result = eventService.getPublishedEvents(
                null,
                null,
                null,
                null,
                null,
                false,
                PublicEventSort.EVENT_DATE,
                0,
                10,
                IP,
                EVENTS_URI
        );

        Assertions.assertThat(result).hasSize(2);
        Assertions.assertThat(result.get(0).getId()).isEqualTo(1L);
        Assertions.assertThat(result.get(0).getViews()).isEqualTo(11L);
        Assertions.assertThat(result.get(1).getId()).isEqualTo(2L);
        Assertions.assertThat(result.get(1).getViews()).isEqualTo(22L);
    }

    @Test
    void getPublishedEvents_doesNotRequestStatsWhenEventsNotFound() {
        Page<Event> emptyPage = new PageImpl<>(List.of());
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        List<EventShortDto> result = eventService.getPublishedEvents(
                null, null, null, null, null,
                false, PublicEventSort.EVENT_DATE,
                0, 10, IP, EVENTS_URI
        );

        Assertions.assertThat(result).isEmpty();
        verify(statsInteractionService, never()).getEventsWithStats(anyList());
    }

    @Test
    void getPublishedEvent_returnsEventWithViewsFromStatsMap() {
        Event event = createEvent(3L, "Published event", EventStatus.PUBLISHED, 0);
        when(eventRepository.findByIdAndStatus(3L, EventStatus.PUBLISHED)).thenReturn(Optional.of(event));

        EventDto baseDto = EventDto.builder()
                .id(3L)
                .title(event.getTitle())
                .category(CategoryDto.builder().id(CATEGORY_ID).build())
                .status(EventStatus.PUBLISHED)
                .views(33L)
                .build();

        when(eventFactoryService.buildEventWithStats(eq(event))).thenReturn(baseDto);

        EventDto result = eventService.getPublishedEvent(3L, IP, "/events/3");

        Assertions.assertThat(result.getId()).isEqualTo(3L);
        Assertions.assertThat(result.getViews()).isEqualTo(33L);
        Assertions.assertThat(result.getCategory().getId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void getEventsIds_returnsExistingIds() {
        List<Long> inputIds = List.of(1L, 2L, 3L, 999L); // 999 — несуществующий
        Set<Long> expected = Set.of(1L, 2L, 3L);

        when(eventRepository.findEventsIds(inputIds)).thenReturn(expected);

        Set<Long> result = eventService.getEventsIds(inputIds);

        assertThat(result).isEqualTo(expected);
        verify(eventRepository).findEventsIds(inputIds);
    }

    @Test
    void getEventsIds_emptyList_returnsEmptySet() {
        List<Long> inputIds = Collections.emptyList();
        Set<Long> expected = Collections.emptySet();

        when(eventRepository.findEventsIds(inputIds)).thenReturn(expected);

        Set<Long> result = eventService.getEventsIds(inputIds);

        assertThat(result).isEmpty();
        verify(eventRepository).findEventsIds(inputIds);
    }

    @Test
    void checkExistenceWithInitiatorId_returnsTrue_whenEventExists() {
        long eventId = 1L;
        long initiatorId = 10L;

        when(eventRepository.existsByIdAndInitiatorId(eventId, initiatorId)).thenReturn(true);

        Boolean result = eventService.checkExistenceWithInitiatorId(eventId, initiatorId);

        assertThat(result).isTrue();
        verify(eventRepository).existsByIdAndInitiatorId(eventId, initiatorId);
    }

    @Test
    void checkExistenceWithInitiatorId_returnsFalse_whenEventDoesNotExist() {
        long eventId = 999L;
        long initiatorId = 10L;

        when(eventRepository.existsByIdAndInitiatorId(eventId, initiatorId)).thenReturn(false);

        Boolean result = eventService.checkExistenceWithInitiatorId(eventId, initiatorId);

        assertThat(result).isFalse();
        verify(eventRepository).existsByIdAndInitiatorId(eventId, initiatorId);
    }

    @Test
    void getEventById_returnsEventDto_whenFound() {
        long eventId = 9L;
        Event event = createTestEvent(eventId, USER_ID, CATEGORY_ID, EventStatus.PUBLISHED);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventDto result = eventService.getEventById(eventId);

        assertThat(result.getId()).isEqualTo(eventId);
        assertThat(result.getInitiator()).isEqualTo(USER_ID);
        assertThat(result.getTitle()).isEqualTo(event.getTitle());
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getEventById_throwsNotFoundException_whenNotFound() {
        long eventId = 999L;

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eventService.getEventById(eventId));
        verify(eventRepository).findById(eventId);
    }

    @Test
    void updateConfirmedRequests_updatesAndReturnsDto() {
        long eventId = 1L;
        int newCount = 5;
        ConfirmedRequestsUpdate body = new ConfirmedRequestsUpdate(newCount);

        Event event = createTestEvent(eventId, USER_ID, CATEGORY_ID, EventStatus.PENDING);
        event.setConfirmedRequests(0);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventDto result = eventService.updateConfirmedRequests(eventId, body);

        assertThat(result.getId()).isEqualTo(eventId);
        assertThat(result.getConfirmedRequests()).isEqualTo(newCount);
        verify(eventRepository).findById(eventId);
        verify(eventRepository).save(event);
    }

    @Test
    void updateConfirmedRequests_throwsNotFoundException_whenEventNotFound() {
        long eventId = 999L;
        ConfirmedRequestsUpdate body = new ConfirmedRequestsUpdate(3);

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> eventService.updateConfirmedRequests(eventId, body));
        verify(eventRepository).findById(eventId);
        verifyNoMoreInteractions(eventRepository);
    }

    @Test
    void getEventShortDtos_returnsShortDtosWithStats() {
        List<Long> ids = List.of(1L, 2L);
        Event e1 = createTestEvent(1L, USER_ID, CATEGORY_ID, EventStatus.PUBLISHED);
        Event e2 = createTestEvent(2L, USER_ID + 1, CATEGORY_ID, EventStatus.PUBLISHED);
        List<Event> events = List.of(e1, e2);

        when(eventRepository.findByIdIn(ids)).thenReturn(events);

        EventDto dto1 = EventDto.builder().id(1L).status(EventStatus.PUBLISHED).views(10L).build();
        EventDto dto2 = EventDto.builder().id(2L).status(EventStatus.PUBLISHED).views(20L).build();
        List<EventDto> baseDtos = List.of(dto1, dto2);

        when(eventFactoryService.buildEventsWithStats(eq(events))).thenReturn(baseDtos);

        List<EventShortDto> result = eventService.getEventShortDtos(ids);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getViews()).isEqualTo(10L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getViews()).isEqualTo(20L);

        verify(eventRepository).findByIdIn(ids);
    }

    @Test
    void getEventShortDtos_noEventsFound_returnsEmptyList() {
        List<Long> ids = List.of(998L, 999L);

        when(eventRepository.findByIdIn(ids)).thenReturn(Collections.emptyList());

        List<EventShortDto> result = eventService.getEventShortDtos(ids);

        assertThat(result).isEmpty();
        verify(eventRepository).findByIdIn(ids);
        verifyNoMoreInteractions(statsClient);
    }

    private Event createEvent(long id, String title, EventStatus status, int confirmedRequests) {
        return Event.builder()
                .id(id)
                .annotation("Annotation with enough length")
                .category(Category.builder().id(CATEGORY_ID).name("Category").build())
                .confirmedRequests(confirmedRequests)
                .createdOn(LocalDateTime.now().minusDays(1))
                .description("Description with enough length")
                .eventDate(LocalDateTime.now().plusDays(id))
                .initiatorId(USER_ID)
                .location(new LocationEmbeddable(55.0, 37.0))
                .paid(false)
                .participantLimit(10)
                .publishedOn(LocalDateTime.now().minusHours(1))
                .requestModeration(true)
                .status(status)
                .title(title)
                .build();
    }

    private Event createTestEvent(long eventId, long userId, long categoryId, EventStatus status) {
        Category category = createTestCategory(categoryId);
        LocationEmbeddable location = new LocationEmbeddable(1.0, 1.0);

        return Event.builder()
                .id(eventId)
                .annotation("Test annotation")
                .category(category)
                .confirmedRequests(0)
                .createdOn(LocalDateTime.now())
                .description("Test description")
                .eventDate(LocalDateTime.now().plusHours(3))
                .initiatorId(userId)
                .location(location)
                .paid(false)
                .participantLimit(10)
                .publishedOn(null)
                .requestModeration(true)
                .status(status)
                .title("Test Event Title")
                .build();
    }

    private Category createTestCategory(long categoryId) {
        return Category.builder()
                .id(categoryId)
                .name("Test Category")
                .build();
    }

    private NewEventDto createTestNewEventDto(Long categoryId, LocalDateTime time) {
        return NewEventDto.builder()
                .annotation("Test annotation for event creation. It must be at least 20 characters long, so we are adding some extra text here to meet the requirement.")
                .category(categoryId)
                .description("Detailed description of the event. Validation requires minimum 20 characters, so this sentence is intentionally long enough to pass the Size constraint.")
                .eventDate(time)
                .location(new Location(55.7558, 37.6176))
                .paid(false)
                .participantLimit(10)
                .requestModeration(true)
                .title("Test Event Title")
                .build();
    }

    private UserUpdateEventDto createTestUpdateEventDto(UserEventUpdateAction action) {
        return UserUpdateEventDto.builder()
                .annotation("Updated annotation text that meets the minimum length of 20 characters requirement for validation.")
                .category(2L)
                .description("Updated detailed description that satisfies the 20–7000 character size constraint.")
                .eventDate(LocalDateTime.now().plusDays(1))
                .location(new Location(59.9343, 30.3351))
                .paid(true)
                .participantLimit(25)
                .requestModeration(false)
                .status(action)
                .title("Updated Event Title")
                .build();
    }

    public AdminUpdateEventDto createUpdateEventDto(AdminEventUpdateAction action) {
        return AdminUpdateEventDto.builder()
                .title("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
                .annotation("A".repeat(20))
                .description("A".repeat(20))
                .category(1L)
                .eventDate(LocalDateTime.now().plusDays(1))
                .location(new Location(59.9343, 30.3351))
                .paid(false)
                .participantLimit(0)
                .requestModeration(false)
                .status(action)
                .build();
    }
}
