package ru.practicum.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.request.dto.RequestStatus;
import ru.practicum.request.entity.Request;
import ru.practicum.request.feign.EventFetchClientAdapter;
import ru.practicum.request.repository.RequestRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RequestRepositoryTest {

    @MockitoBean
    private EventFetchClientAdapter eventFetchClientAdapter;

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @MockitoBean
    private EventExistsClientAdapter eventExistsClientAdapter;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final long EVENT_ID = 10L;
    private static final long OTHER_EVENT_ID = 20L;

    private Request persistRequest(long eventId, long requesterId, RequestStatus status, LocalDateTime created) {
        return entityManager.persist(Request.builder()
                .eventId(eventId)
                .requesterId(requesterId)
                .status(status)
                .created(created)
                .build());
    }

    @Test
    @DisplayName("findByEventId: все заявки события, независимо от статуса и автора")
    void findByEventId_returnsAllOfEvent() {
        persistRequest(EVENT_ID, 1L, RequestStatus.PENDING, LocalDateTime.now());
        persistRequest(EVENT_ID, 2L, RequestStatus.CONFIRMED, LocalDateTime.now());
        persistRequest(EVENT_ID, 3L, RequestStatus.CANCELED, LocalDateTime.now());
        persistRequest(OTHER_EVENT_ID, 4L, RequestStatus.CONFIRMED, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        List<Request> result = requestRepository.findByEventId(EVENT_ID);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(r -> r.getEventId() == EVENT_ID);
    }

    @Test
    @DisplayName("findByEventId: несуществующее событие — пустой список")
    void findByEventId_unknownEvent_returnsEmpty() {
        assertThat(requestRepository.findByEventId(999L)).isEmpty();
    }

    @Test
    @DisplayName("findByIdIn: возвращает только существующие заявки, лишние id игнорируются")
    void findByIdIn_returnsOnlyExisting() {
        Request r1 = persistRequest(EVENT_ID, 1L, RequestStatus.CONFIRMED, LocalDateTime.now());
        Request r2 = persistRequest(EVENT_ID, 2L, RequestStatus.PENDING, LocalDateTime.now());
        persistRequest(OTHER_EVENT_ID, 3L, RequestStatus.CONFIRMED, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        List<Request> result = requestRepository.findByIdIn(
                List.of(r1.getId(), r2.getId(), 999L));

        assertThat(result).extracting(Request::getId)
                .containsExactlyInAnyOrder(r1.getId(), r2.getId());
    }

    @Test
    @DisplayName("findByRequesterIdOrderByCreatedDesc: заявки автора, отсортированные по created убыванию")
    void findByRequesterIdOrderByCreatedDesc_sortedByCreatedDescending() {
        LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        persistRequest(EVENT_ID, 7L, RequestStatus.CONFIRMED, base);
        persistRequest(OTHER_EVENT_ID, 7L, RequestStatus.PENDING, base.plusMinutes(2));
        persistRequest(EVENT_ID, 7L, RequestStatus.CANCELED, base.plusMinutes(1));
        persistRequest(EVENT_ID, 8L, RequestStatus.CONFIRMED, base.plusMinutes(3)); // чужой
        entityManager.flush();
        entityManager.clear();

        List<Request> result = requestRepository.findByRequesterIdOrderByCreatedDesc(7L);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(r -> r.getRequesterId() == 7L);
        // созданы base, +1 мин, +2 мин — по убыванию: +2, +1, base
        assertThat(result).extracting(Request::getCreated)
                .containsExactly(base.plusMinutes(2), base.plusMinutes(1), base);
    }

    @Test
    @DisplayName("findByRequesterIdOrderByCreatedDesc: у автора нет заявок — пустой список")
    void findByRequesterIdOrderByCreatedDesc_unknownRequester_returnsEmpty() {
        assertThat(requestRepository.findByRequesterIdOrderByCreatedDesc(999L)).isEmpty();
    }

    @Test
    @DisplayName("existsByEventIdAndRequesterId: оба параметра должны совпасть")
    void existsByEventIdAndRequesterId_checksBothParams() {
        persistRequest(EVENT_ID, 5L, RequestStatus.CONFIRMED, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, 5L)).isTrue();
        assertThat(requestRepository.existsByEventIdAndRequesterId(EVENT_ID, 6L)).isFalse();
        assertThat(requestRepository.existsByEventIdAndRequesterId(OTHER_EVENT_ID, 5L)).isFalse();
    }

    @Test
    @DisplayName("existsByEventIdAndRequesterIdAndStatus: true только при совпадении всех трёх")
    void existsByEventIdAndRequesterIdAndStatus_checksAllParams() {
        persistRequest(EVENT_ID, 5L, RequestStatus.CONFIRMED, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(requestRepository.existsByEventIdAndRequesterIdAndStatus(
                EVENT_ID, 5L, RequestStatus.CONFIRMED)).isTrue();
        assertThat(requestRepository.existsByEventIdAndRequesterIdAndStatus(
                EVENT_ID, 5L, RequestStatus.PENDING)).isFalse();
        assertThat(requestRepository.existsByEventIdAndRequesterIdAndStatus(
                EVENT_ID, 6L, RequestStatus.CONFIRMED)).isFalse();
        assertThat(requestRepository.existsByEventIdAndRequesterIdAndStatus(
                OTHER_EVENT_ID, 5L, RequestStatus.CONFIRMED)).isFalse();
    }
}