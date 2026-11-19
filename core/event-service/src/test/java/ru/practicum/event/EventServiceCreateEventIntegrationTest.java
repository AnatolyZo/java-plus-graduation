package ru.practicum.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.event.dto.event.EventDto;
import ru.practicum.event.dto.event.EventStatus;
import ru.practicum.event.dto.event.Location;
import ru.practicum.event.dto.event.NewEventDto;
import ru.practicum.event.entity.Category;
import ru.practicum.event.entity.Event;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.repository.CategoryRepository;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.service.EventService;
import ru.practicum.feign.adapters.UserExistsClientAdapter;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
import ru.practicum.feign.result.RemoteCallResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static ru.practicum.common.ControllerConstants.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventServiceCreateEventIntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    private long categoryId;

    private NewEventDto newEventDto;

    private NewEventDto buildNewEventDto(long categoryId) {
        return NewEventDto.builder()
                .title("Integration Test Event")
                .description("Description created by integration test")
                .annotation("Integration test annotation")
                .category(categoryId)
                .eventDate(LocalDateTime.now().plusDays(2))
                .location(new Location(59.9343, 30.3351))
                .paid(false)
                .participantLimit(10)
                .requestModeration(true)
                .build();
    }

    private String eventJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", "Тестовое событие",
                "annotation", "Достаточно длинная аннотация события",
                "description", "Достаточно длинное описание события для валидации",
                "eventDate", "2027-06-01 12:00:00",
                "location", Map.of("lat", 55.75, "lon", 37.61),
                "category", categoryId,
                "paid", false,
                "participantLimit", 10
        ));
    }

    private String categoryJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", "Тестовая категория"));
    }

    @BeforeEach
    void setUp() throws Exception {
        eventRepository.deleteAll();
        categoryRepository.deleteAll();
        categoryId = createCategory();
    }

    @AfterEach
    void resetCircuitBreaker() {
        registry.circuitBreaker("user-checker").reset();
    }

    @Test
    void createEvent_savesEventAndReturnsCorrectDto() {
        long userId = 1L;
        Category category = categoryRepository.save(
                Category.builder().name("Concerts").build());
        newEventDto = buildNewEventDto(category.getId());

        when(userExistsClientAdapter.checkExists(List.of(userId)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(userId)));

        EventDto result = eventService.createEvent(userId, newEventDto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Integration Test Event");
        assertThat(result.getStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(result.getCategory().getId()).isEqualTo(category.getId());
        assertThat(result.getInitiator()).isEqualTo(userId);

        Event saved = eventRepository.findById(result.getId()).orElseThrow();
        assertThat(saved.getInitiatorId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(saved.getCreatedOn()).isNotNull();
    }

    @Test
    void createEvent_throwsNotFoundException_whenCategoryDoesNotExist() {
        long userId = 1L;
        newEventDto = buildNewEventDto(999L);

        when(userExistsClientAdapter.checkExists(List.of(userId)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(userId)));

        assertThatThrownBy(() -> eventService.createEvent(userId, newEventDto))
                .isInstanceOf(NotFoundException.class);

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void createEvent_throwsNotFoundException_whenUserDoesNotExist() {
        long userId = 1L;
        Category category = categoryRepository.save(
                Category.builder().name("Concerts").build());
        newEventDto = buildNewEventDto(category.getId());

        when(userExistsClientAdapter.checkExists(List.of(userId)))
                .thenReturn(new RemoteCallResult.Success<>(Set.of(2L)));

        assertThatThrownBy(() -> eventService.createEvent(userId, newEventDto))
                .isInstanceOf(NotFoundException.class);

        assertThat(eventRepository.count()).isZero();
    }

    private Long createCategory() throws Exception {
        String response = mockMvc.perform(post("/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.parse(response).read("$.id", Long.class);
    }

    @Test
    void circuitOpensAfterWindowOfFiveTechnicalFailures() throws Exception {
        when(userExistsClientAdapter.checkExists(any()))
                .thenThrow(new RemoteCallTechnicalException("connection refused"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(ACCESS_PRIVATE + "/1" + URL_EVENTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(eventJson()))
                    .andExpect(status().isServiceUnavailable()); // 503 из fallback
        }

        CircuitBreaker cb = registry.circuitBreaker("user-checker");

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        // шестой вызов отсекается мгновенно и НЕ доходит до адаптера
        mockMvc.perform(post(ACCESS_PRIVATE + "/1" + URL_EVENTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson()))
                .andExpect(status().isServiceUnavailable());

        verify(userExistsClientAdapter, times(5)).checkExists(any());
    }

    @Test
    void businessErrorsDoNotOpenCircuit() throws Exception {
        when(userExistsClientAdapter.checkExists(any()))
                .thenThrow(new RemoteCallBusinessException(404, "user not found")); // в ignore-exceptions

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(ACCESS_PRIVATE + "/999" + URL_EVENTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(eventJson()))
                    .andExpect(status().isNotFound()); // пролетает насквозь, не 503
        }

        assertThat(registry.circuitBreaker("user-checker").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED); // бизнес-ошибки не считаются сбоями
    }
}