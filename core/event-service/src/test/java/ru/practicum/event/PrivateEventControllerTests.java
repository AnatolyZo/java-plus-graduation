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
import ru.practicum.event.controller.event.PrivateEventController;
import ru.practicum.event.dto.category.CategoryDto;
import ru.practicum.event.dto.event.*;
import ru.practicum.event.service.EventService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PrivateEventControllerTests {
    private static final long TEST_USER_ID = 1L;
    private static final long TEST_EVENT_ID = 1L;

    @Mock
    private EventService eventService;

    @InjectMocks
    private PrivateEventController controller;

    private NewEventDto postRequest;
    private UserUpdateEventDto updateRequest;
    private EventDto response;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime time = now.plusHours(3);
        LocalDateTime created = now.minusDays(1);
        Location location = new Location(1, 1);
        long initiator = TEST_USER_ID;
        CategoryDto category = CategoryDto.builder()
                .id(1L)
                .name("Category")
                .build();

        postRequest = NewEventDto.builder()
                .annotation("AnnotationAnnotationAnnotationAnnotation")
                .category(1L)
                .description("DescriptionDescriptionDescriptionDescription")
                .eventDate(time)
                .location(location)
                .paid(true)
                .participantLimit(10)
                .requestModeration(true)
                .title("TitleTitleTitleTitleTitle")
                .build();

        updateRequest = UserUpdateEventDto.builder()
                .annotation("AnnotationAnnotationAnnotationAnnotation")
                .category(1L)
                .description("DescriptionDescriptionDescriptionDescription")
                .eventDate(time)
                .location(location)
                .paid(true)
                .participantLimit(10)
                .requestModeration(true)
                .status(UserEventUpdateAction.SEND_TO_REVIEW)
                .title("TitleTitleTitleTitleTitle")
                .build();

        response = EventDto.builder()
                .id(TEST_EVENT_ID)
                .annotation("AnnotationAnnotationAnnotationAnnotation")
                .category(category)
                .confirmedRequests(10)
                .createdOn(created)
                .description("DescriptionDescriptionDescriptionDescription")
                .eventDate(time)
                .initiator(initiator)
                .location(location)
                .paid(true)
                .participantLimit(10)
                .requestModeration(true)
                .status(EventStatus.PENDING)
                .title("TitleTitleTitleTitleTitle")
                .build();
    }

    @Test
    void createEvent_returnsCreated() {
        when(eventService.createEvent(eq(TEST_USER_ID), any(NewEventDto.class)))
                .thenReturn(response);

        ResponseEntity<EventDto> result = controller.createEvent(TEST_USER_ID, postRequest);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_EVENT_ID);
        Assertions.assertThat(result.getBody().getTitle()).isEqualTo("TitleTitleTitleTitleTitle");
    }

    @Test
    void getEvents_returnsOkAndList() {
        int from = 1;
        int size = 1;

        when(eventService.getEvents(eq(TEST_USER_ID), eq(from), eq(size)))
                .thenReturn(List.of(response));

        ResponseEntity<List<EventDto>> result = controller.getEvents(TEST_USER_ID, from, size);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(1);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(TEST_EVENT_ID);
    }

    @Test
    void getEvent_returnsOk() {
        when(eventService.getEvent(eq(TEST_USER_ID), eq(TEST_EVENT_ID)))
                .thenReturn(response);

        ResponseEntity<EventDto> result = controller.getEvent(TEST_USER_ID, TEST_EVENT_ID);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_EVENT_ID);
        Assertions.assertThat(result.getBody().getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    void updateEvent_returnsOk() {
        when(eventService.updateEvent(eq(TEST_USER_ID), eq(TEST_EVENT_ID), any(UserUpdateEventDto.class)))
                .thenReturn(response);

        ResponseEntity<EventDto> result = controller.updateEvent(TEST_USER_ID, TEST_EVENT_ID, updateRequest);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(TEST_EVENT_ID);
        Assertions.assertThat(result.getBody().getStatus()).isEqualTo(EventStatus.PENDING);
    }
}
