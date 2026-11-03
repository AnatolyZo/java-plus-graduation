package ru.practicum.event;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.practicum.event.controller.event.AdminEventController;
import ru.practicum.event.dto.category.CategoryDto;
import ru.practicum.event.dto.event.*;
import ru.practicum.event.service.EventService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminEventControllerTests {
    private static final long TEST_EVENT_ID = 1L;
    private static final long TEST_INITIATOR_ID = 10L;
    private static final int NEW_CONFIRMED_REQUESTS_COUNT = 5;

    @Mock
    private EventService eventService;

    @InjectMocks
    private AdminEventController controller;

    private AdminUpdateEventDto updateRequest;
    private EventDto responseEvent;
    private List<EventDto> responseList;

    @BeforeEach
    void setUp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 10, 14, 0, 0);
        LocalDateTime created = LocalDateTime.of(2026, 7, 10, 14, 0, 0);
        Location location = new Location(1, 1);
        CategoryDto categoryDto = CategoryDto.builder()
                .id(1L)
                .name("Name")
                .build();

        long initiator = 1L;

        updateRequest = AdminUpdateEventDto.builder()
                .title("ValidTitle123456789012")
                .annotation("A".repeat(20))
                .description("B".repeat(20))
                .category(1L)
                .eventDate(time)
                .location(location)
                .paid(false)
                .participantLimit(10)
                .requestModeration(false)
                .status(AdminEventUpdateAction.PUBLISH_EVENT)
                .build();

        responseEvent = EventDto.builder()
                .id(TEST_EVENT_ID)
                .annotation("AnnotationAnnotationAnnotationAnnotation")
                .category(categoryDto)
                .confirmedRequests(10)
                .createdOn(created)
                .description("DescriptionDescriptionDescriptionDescription")
                .eventDate(time)
                .initiator(initiator)
                .location(location)
                .paid(true)
                .participantLimit(10)
                .requestModeration(true)
                .status(EventStatus.PUBLISHED)
                .title("TitleTitleTitleTitleTitle")
                .views(42L)
                .build();

        responseList = List.of(responseEvent);
    }

    @Test
    void searchEvents_defaultParams_returnsOkAndList() {
        when(eventService.searchEvents(null, null, null, null, null, 0, 10))
                .thenReturn(responseList);

        ResponseEntity<List<EventDto>> result = controller.searchEvents(null, null, null, null, null, 0, 10);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(TEST_EVENT_ID);
    }

    @Test
    void searchEvents_withFiltersAndPagination_returnsOkAndFilteredList() {
        List<Long> users = List.of(1L, 2L);
        List<String> states = List.of("PUBLISHED", "CANCELLED");
        List<Long> categories = List.of(3L);
        String rangeStart = "2024-01-01 00:00:00";
        String rangeEnd = "2024-12-31 23:59:59";
        int from = 5;
        int size = 10;

        when(eventService.searchEvents(users, states, categories, rangeStart, rangeEnd, from, size))
                .thenReturn(responseList);

        ResponseEntity<List<EventDto>> result = controller.searchEvents(
                users, states, categories, rangeStart, rangeEnd, from, size);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void searchEvents_emptyResult_returnsEmptyList() {
        ResponseEntity<List<EventDto>> result = controller.searchEvents(null, null, null, null, null, 0, 10);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).isEmpty();
    }

    @Test
    void updateEvent_validDto_returnsOk() {
        when(eventService.updateEvent(eq(TEST_EVENT_ID), any(AdminUpdateEventDto.class)))
                .thenReturn(responseEvent);

        ResponseEntity<EventDto> result = controller.updateEvent(TEST_EVENT_ID, updateRequest);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_EVENT_ID);
        Assertions.assertThat(result.getBody().getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void checkExistenceWithInitiatorId_eventExists_returnsTrue() {
        when(eventService.checkExistenceWithInitiatorId(eq(TEST_EVENT_ID), eq(TEST_INITIATOR_ID)))
                .thenReturn(true);

        ResponseEntity<Boolean> result = controller.checkExistenceWithInitiatorId(TEST_EVENT_ID, TEST_INITIATOR_ID);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isEqualTo(true);
    }

    @Test
    void checkExistenceWithInitiatorId_eventDoesNotExist_returnsFalse() {
        long nonExistingEventId = 999L;
        long initiatorId = 10L;

        when(eventService.checkExistenceWithInitiatorId(eq(nonExistingEventId), eq(initiatorId)))
                .thenReturn(false);

        ResponseEntity<Boolean> result = controller.checkExistenceWithInitiatorId(nonExistingEventId, initiatorId);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isEqualTo(false);
    }

    @Test
    void getEventById_returnsOkAndEventDto() {
        when(eventService.getEventById(eq(TEST_EVENT_ID))).thenReturn(responseEvent);

        ResponseEntity<EventDto> result = controller.getEventById(TEST_EVENT_ID);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_EVENT_ID);
        Assertions.assertThat(result.getBody().getTitle()).isEqualTo("TitleTitleTitleTitleTitle");
        Assertions.assertThat(result.getBody().getViews()).isEqualTo(42L);
    }

    @Test
    void updateConfirmedRequests_validBody_returnsOk() {
        ConfirmedRequestsUpdate body = new ConfirmedRequestsUpdate(NEW_CONFIRMED_REQUESTS_COUNT);

        EventDto updatedEvent = EventDto.builder()
                .id(TEST_EVENT_ID)
                .confirmedRequests(NEW_CONFIRMED_REQUESTS_COUNT)
                .build();

        when(eventService.updateConfirmedRequests(eq(TEST_EVENT_ID), any(ConfirmedRequestsUpdate.class)))
                .thenReturn(updatedEvent);

        ResponseEntity<EventDto> result = controller.updateConfirmedRequests(TEST_EVENT_ID, body);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_EVENT_ID);
        Assertions.assertThat(result.getBody().getConfirmedRequests()).isEqualTo(NEW_CONFIRMED_REQUESTS_COUNT);
    }

    @Test
    void getEventShortDtos_withIdsParam_returnsOkAndList() {
        List<Long> ids = List.of(1L, 2L);

        Location location = new Location(1, 1);
        CategoryDto categoryDto = CategoryDto.builder().id(1L).name("Name").build();
        EventShortDto dto1 = EventShortDto.builder()
                .id(1L)
                .title("First Event")
                .annotation("Annotation 1")
                .category(categoryDto)
                .eventDate(LocalDateTime.of(2026, 8, 10, 14, 0, 0))
                .initiator(1L)
                .paid(false)
                .views(10L)
                .confirmedRequests(2)
                .build();
        EventShortDto dto2 = EventShortDto.builder()
                .id(2L)
                .title("Second Event")
                .annotation("Annotation 2")
                .category(categoryDto)
                .eventDate(LocalDateTime.of(2026, 8, 11, 15, 0, 0))
                .initiator(2L)
                .paid(true)
                .views(20L)
                .confirmedRequests(5)
                .build();
        List<EventShortDto> resultList = List.of(dto1, dto2);

        when(eventService.getEventShortDtos(ids)).thenReturn(resultList);

        ResponseEntity<List<EventShortDto>> result = controller.getEventShortDtos(ids);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(2);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(1L);
        Assertions.assertThat(result.getBody().get(0).getTitle()).isEqualTo("First Event");
        Assertions.assertThat(result.getBody().get(1).getId()).isEqualTo(2L);
    }

    @Test
    void getEventShortDtos_nullOrEmptyIds_returnsEmptyList() {
        // Контроллер сам обрабатывает null/empty и возвращает пустой список
        ResponseEntity<List<EventShortDto>> resultNull = controller.getEventShortDtos(null);
        ResponseEntity<List<EventShortDto>> resultEmpty = controller.getEventShortDtos(Collections.emptyList());

        Assertions.assertThat(resultNull.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resultNull.getBody()).isNotNull().isEmpty();

        Assertions.assertThat(resultEmpty.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resultEmpty.getBody()).isNotNull().isEmpty();
    }

    @Test
    void checkExists_nullOrEmptyIds_returnsEmptySet() {
        ResponseEntity<Set<Long>> resultNull = controller.checkExists(null);
        ResponseEntity<Set<Long>> resultEmpty = controller.checkExists(Collections.emptyList());

        Assertions.assertThat(resultNull.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resultNull.getBody()).isNotNull().isEmpty();

        Assertions.assertThat(resultEmpty.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resultEmpty.getBody()).isNotNull().isEmpty();
    }
}
