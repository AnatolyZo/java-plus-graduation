package ru.practicum.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.event.dto.event.*;
import ru.practicum.event.entity.Category;
import ru.practicum.event.entity.Event;
import ru.practicum.event.entity.LocationEmbeddable;
import ru.practicum.event.service.support.EventFactoryService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForClassTypes.offset;

@ExtendWith(MockitoExtension.class)
public class EventFactoryServiceTests {

    @InjectMocks
    private EventFactoryService service;

    private NewEventDto newEventDto() {
        return NewEventDto.builder()
                .annotation("new annotation")
                .description("new description")
                .eventDate(LocalDateTime.of(2027, 1, 1, 12, 0))
                .category(5L)
                .location(new Location(55.75, 37.62))
                .paid(true)
                .participantLimit(100)
                .requestModeration(true)
                .title("new title")
                .build();
    }

    private Event event(EventStatus status) {
        return Event.builder()
                .id(1L)
                .annotation("old annotation")
                .description("old description")
                .eventDate(LocalDateTime.of(2026, 1, 1, 12, 0))
                .paid(false)
                .participantLimit(10)
                .requestModeration(false)
                .title("old title")
                .status(status)
                .location(new LocationEmbeddable(55.0, 37.0))
                .category(category(5L, "default category"))
                .initiatorId(42L)
                .createdOn(LocalDateTime.of(2025, 12, 1, 10, 0))
                .build();
    }

    private Category category(long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .build();
    }

    // ---------- updateEventFields ----------

    @Test
    @DisplayName("непустые поля применяются, status ставится всегда")
    void updateAppliesAllNonEmptyFields() {
        Event event = event(EventStatus.PENDING);

        EventUpdateCommon update = AdminUpdateEventDto.builder()
                .annotation("new annotation")
                .description("new description")
                .eventDate(LocalDateTime.of(2027, 6, 1, 10, 0))
                .location(new Location(56.5, 38.5))
                .paid(true)
                .participantLimit(50)
                .requestModeration(true)
                .title("new title")
                .build();

        service.updateEventFields(event, update, EventStatus.PUBLISHED);

        assertThat(event.getAnnotation()).isEqualTo("new annotation");
        assertThat(event.getDescription()).isEqualTo("new description");
        assertThat(event.getEventDate()).isEqualTo(LocalDateTime.of(2027, 6, 1, 10, 0));
        assertThat(event.getLocation().getLat()).isEqualTo(56.5);
        assertThat(event.getLocation().getLon()).isEqualTo(38.5);
        assertThat(event.isPaid()).isTrue();
        assertThat(event.getParticipantLimit()).isEqualTo(50);
        assertThat(event.isRequestModeration()).isTrue();
        assertThat(event.getTitle()).isEqualTo("new title");
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("null и пустые строки игнорируются — старые значения сохраняются")
    void updateIgnoresNullAndEmptyFields() {
        Event event = event(EventStatus.PENDING);

        EventUpdateCommon update = AdminUpdateEventDto.builder().build();

        service.updateEventFields(event, update, EventStatus.PENDING);

        assertThat(event.getAnnotation()).isEqualTo("old annotation");
        assertThat(event.getDescription()).isEqualTo("old description");
        assertThat(event.getEventDate()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
        assertThat(event.isPaid()).isFalse();
        assertThat(event.getParticipantLimit()).isEqualTo(10);
        assertThat(event.isRequestModeration()).isFalse();
        assertThat(event.getTitle()).isEqualTo("old title");
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("status применяется даже при полностью пустом update")
    void updateSetsStatusEvenWithEmptyUpdate() {
        Event event = event(EventStatus.PENDING);

        service.updateEventFields(event, AdminUpdateEventDto.builder().build(), EventStatus.CANCELED);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELED);
    }

    // ---------- changeEventStatus (пользователь) ----------

    @Test
    @DisplayName("CANCEL_REVIEW -> CANCELED")
    void userCancelReviewGivesCanceled() {
        UserUpdateEventDto update = UserUpdateEventDto.builder()
                .status(UserEventUpdateAction.CANCEL_REVIEW)
                .build();

        assertThat(service.changeEventStatus(update)).isEqualTo(EventStatus.CANCELED);
    }

    @Test
    @DisplayName("SEND_TO_REVIEW -> PENDING")
    void userSendToReviewGivesPending() {
        UserUpdateEventDto update = UserUpdateEventDto.builder()
                .status(UserEventUpdateAction.SEND_TO_REVIEW)
                .build();

        assertThat(service.changeEventStatus(update)).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("status не задан -> PENDING")
    void userNullStatusGivesPending() {
        UserUpdateEventDto update = UserUpdateEventDto.builder().build();

        assertThat(service.changeEventStatus(update)).isEqualTo(EventStatus.PENDING);
    }

    // ---------- changeEventStatus (админ) ----------

    @Test
    @DisplayName("PUBLISH_EVENT -> PUBLISHED, publishedOn проставлен текущим временем")
    void adminPublishSetsPublishedAndPublishedOn() {
        Event event = event(EventStatus.PENDING);

        AdminUpdateEventDto update = AdminUpdateEventDto.builder()
                .status(AdminEventUpdateAction.PUBLISH_EVENT)
                .build();

        EventStatus result = service.changeEventStatus(event, update);

        assertThat(result).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedOn())
                .isCloseTo(LocalDateTime.now(), within(2, ChronoUnit.SECONDS));
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("REJECT_EVENT -> CANCELED, publishedOn не трогается")
    void adminRejectSetsCanceled() {
        Event event = event(EventStatus.PENDING);

        AdminUpdateEventDto update = AdminUpdateEventDto.builder()
                .status(AdminEventUpdateAction.REJECT_EVENT)
                .build();

        EventStatus result = service.changeEventStatus(event, update);

        assertThat(result).isEqualTo(EventStatus.CANCELED);
        assertThat(event.getPublishedOn()).isNull();
    }

    @Test
    @DisplayName("status не задан -> статус события сохраняется")
    void adminNullStatusKeepsEventStatus() {
        Event event = event(EventStatus.PUBLISHED);

        AdminUpdateEventDto update = AdminUpdateEventDto.builder().build();

        EventStatus result = service.changeEventStatus(event, update);

        assertThat(result).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedOn()).isNull();
    }

    // ---------- buildEvent ----------

    @Test
    @DisplayName("buildEvent: category, initiatorId, location проставлены, статус PENDING, createdOn около now")
    void buildEventFillsNestedAndCreationParams() {
        NewEventDto dto = newEventDto();
        Category category = category(5L, "concerts");
        LocationEmbeddable location = new LocationEmbeddable(55.75, 37.62);

        Event event = service.buildEvent(dto, category, 42L, location);

        assertThat(event.getCategory()).isSameAs(category);
        assertThat(event.getInitiatorId()).isEqualTo(42L);
        assertThat(event.getLocation()).isSameAs(location);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(event.isRequestModeration()).isTrue();
        assertThat(event.getCreatedOn())
                .isCloseTo(LocalDateTime.now(), within(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("buildEvent при requestModeration=null берёт дефолт из requestModerationOrDefault (true)")
    void buildEventUsesDefaultModerationWhenNull() {
        NewEventDto dto = newEventDto();

        Event event = service.buildEvent(dto, category(5L, "concerts"), 42L,
                new LocationEmbeddable(55.75, 37.62));

        assertThat(event.isRequestModeration()).isTrue();
    }

    // ---------- buildEventDto ----------

    @Test
    @DisplayName("buildEventDto: category, initiator и location переносятся в DTO")
    void buildEventDtoFillsNestedValues() {
        Event event = event(EventStatus.PUBLISHED);
        event.setCategory(category(7L, "theater"));
        event.setInitiatorId(99L);

        EventDto eventDto = service.buildEventDto(event);

        assertThat(eventDto.getId()).isEqualTo(1L);
        assertThat(eventDto.getCategory().getId()).isEqualTo(7L);
        assertThat(eventDto.getCategory().getName()).isEqualTo("theater");
        assertThat(eventDto.getInitiator()).isEqualTo(99L);
        assertThat(eventDto.getLocation().lat()).isEqualTo(55.0);
        assertThat(eventDto.getLocation().lon()).isEqualTo(37.0);
    }

    // ---------- buildEventsWithStats ----------

    @Test
    @DisplayName("рейтинг из карты проставляется, отсутствующим — 0.0")
    void ratingsAreSetWithZeroDefault() {
        Event first = event(EventStatus.PENDING);
        first.setId(1L);
        Event second = event(EventStatus.PENDING);
        second.setId(2L);

        List<EventDto> result = service.buildEventsWithStats(
                List.of(first, second), Map.of(1L, 4.2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getRating()).isEqualTo(4.2, offset(1e-9));
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getRating()).isEqualTo(0.0, offset(1e-9));
    }

    // ---------- buildEventsShortWithStats ----------

    @Test
    @DisplayName("короткие DTO отсортированы по рейтингу по убыванию; без рейтинга — 0.0")
    void shortDtosSortedByRatingDesc() {
        Event low = event(EventStatus.PENDING);
        low.setId(1L);
        Event high = event(EventStatus.PENDING);
        high.setId(3L);
        Event noRating = event(EventStatus.PENDING);
        noRating.setId(2L);

        List<EventShortDto> result = service.buildEventsShortWithStats(
                List.of(low, high, noRating), Map.of(1L, 1.5, 3L, 4.2));

        assertThat(result).extracting(EventShortDto::getId)
                .containsExactly(3L, 1L, 2L);
        assertThat(result.get(0).getRating()).isEqualTo(4.2, offset(1e-9));
        assertThat(result.get(2).getRating()).isEqualTo(0.0, offset(1e-9));
    }
}
