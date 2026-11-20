package ru.practicum.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.event.dto.event.EventStatus;
import ru.practicum.event.entity.Category;
import ru.practicum.event.entity.Event;
import ru.practicum.event.entity.LocationEmbeddable;
import ru.practicum.event.feign.RequestExistsClientAdapter;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EventRepositoryTest {

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @MockitoBean
    private EventExistsClientAdapter eventExistsClientAdapter;

    @MockitoBean
    private RequestExistsClientAdapter requestExistsClientAdapter;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category category;

    private Event persistEvent(long initiatorId, String title, EventStatus status) {
        if (category == null) {
            category = entityManager.persist(Category.builder().name("Concerts").build());
        }
        return entityManager.persist(Event.builder()
                .title(title)
                .annotation("annotation of " + title)
                .description("description of " + title)
                .eventDate(LocalDateTime.now().plusDays(1))
                .createdOn(LocalDateTime.now().minusHours(1))
                .initiatorId(initiatorId)
                .category(category)
                .location(new LocationEmbeddable(55.75, 37.61))
                .paid(false)
                .participantLimit(10)
                .requestModeration(true)
                .status(status)
                .build());
    }

    @Test
    @DisplayName("existsByCategoryId: true, если у категории есть события")
    void existsByCategoryId_trueWhenEventsExist() {
        Category other = entityManager.persist(Category.builder().name("Empty").build());
        persistEvent(1L, "e1", EventStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        assertThat(eventRepository.existsByCategoryId(category.getId())).isTrue();
        assertThat(eventRepository.existsByCategoryId(other.getId())).isFalse();
    }

    @Test
    @DisplayName("existsByIdAndInitiatorId: оба параметра должны совпасть")
    void existsByIdAndInitiatorId_checksBothParams() {
        Event event = persistEvent(5L, "owned", EventStatus.PUBLISHED);
        entityManager.flush();
        entityManager.clear();

        assertThat(eventRepository.existsByIdAndInitiatorId(event.getId(), 5L)).isTrue();
        assertThat(eventRepository.existsByIdAndInitiatorId(event.getId(), 6L)).isFalse();
        assertThat(eventRepository.existsByIdAndInitiatorId(999L, 5L)).isFalse();
    }

    @Test
    @DisplayName("findByInitiatorId: события одного автора, пагинация и сортировка по id")
    void findByInitiatorId_paginatesAndSorts() {
        Event first = persistEvent(7L, "first", EventStatus.PUBLISHED);
        persistEvent(7L, "second", EventStatus.PUBLISHED);
        persistEvent(8L, "foreign", EventStatus.PUBLISHED);
        entityManager.flush();
        entityManager.clear();

        List<Event> page = eventRepository.findByInitiatorId(
                7L, PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(page).hasSize(1);
        assertThat(page.get(0).getId()).isEqualTo(first.getId());
        assertThat(page.get(0).getInitiatorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("findByIdAndInitiatorId: находит только своё событие")
    void findByIdAndInitiatorId_returnsOwnEventOnly() {
        Event event = persistEvent(5L, "own", EventStatus.PENDING);
        persistEvent(6L, "not mine", EventStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        assertThat(eventRepository.findByIdAndInitiatorId(event.getId(), 5L))
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.getTitle()).isEqualTo("own"));
        assertThat(eventRepository.findByIdAndInitiatorId(event.getId(), 6L)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndStatus: совпадение и по id, и по статусу")
    void findByIdAndStatus_checksBothParams() {
        Event pending = persistEvent(1L, "pending", EventStatus.PENDING);
        persistEvent(1L, "published", EventStatus.PUBLISHED);
        entityManager.flush();
        entityManager.clear();

        assertThat(eventRepository.findByIdAndStatus(pending.getId(), EventStatus.PENDING))
                .hasValueSatisfying(e -> assertThat(e.getTitle()).isEqualTo("pending"));
        assertThat(eventRepository.findByIdAndStatus(pending.getId(), EventStatus.PUBLISHED)).isEmpty();
    }

    @Test
    @DisplayName("findByIdIn: возвращает только существующие id, лишние игнорируются")
    void findByIdIn_returnsOnlyExisting() {
        Event e1 = persistEvent(1L, "one", EventStatus.PUBLISHED);
        Event e2 = persistEvent(2L, "two", EventStatus.CANCELED);
        entityManager.flush();
        entityManager.clear();

        List<Event> result = eventRepository.findByIdIn(
                List.of(e1.getId(), e2.getId(), 999L));

        assertThat(result).extracting(Event::getId)
                .containsExactlyInAnyOrder(e1.getId(), e2.getId());
    }

    @Test
    @DisplayName("findEventsIds: нативный @Query возвращает id из переданного списка")
    void findEventsIds_returnsIdsOfExistingEvents() {
        Event e1 = persistEvent(1L, "one", EventStatus.PUBLISHED);
        Event e2 = persistEvent(2L, "two", EventStatus.PUBLISHED);
        entityManager.flush();
        entityManager.clear();

        Set<Long> result = eventRepository.findEventsIds(
                List.of(e1.getId(), e2.getId(), 999L));

        assertThat(result).containsExactlyInAnyOrder(e1.getId(), e2.getId());
        assertThat(result).doesNotContain(999L);
    }

    @Test
    @DisplayName("findEventsIds: пустой список — пустое множество")
    void findEventsIds_emptyInput_returnsEmptySet() {
        assertThat(eventRepository.findEventsIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("JpaSpecificationExecutor: базовая фильтрация по статусу через Specification")
    void specificationExecutor_filtersByStatus() {
        persistEvent(1L, "pub-1", EventStatus.PUBLISHED);
        persistEvent(2L, "pub-2", EventStatus.PUBLISHED);
        persistEvent(3L, "pend", EventStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        List<Event> published = eventRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("status"), EventStatus.PUBLISHED));

        assertThat(published).hasSize(2);
        assertThat(published).allMatch(e -> e.getStatus() == EventStatus.PUBLISHED);
    }
}