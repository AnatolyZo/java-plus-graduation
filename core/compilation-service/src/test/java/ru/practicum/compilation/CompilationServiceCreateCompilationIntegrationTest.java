package ru.practicum.compilation;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.entity.Compilation;
import ru.practicum.compilation.feign.EventFetchClientAdapter;
import ru.practicum.compilation.feign.EventShortDto;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.compilation.service.CompilationsService;
import ru.practicum.feign.exception.RemoteCallBusinessException;
import ru.practicum.feign.exception.RemoteCallTechnicalException;
import ru.practicum.feign.result.RemoteCallResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CompilationServiceCreateCompilationIntegrationTest {

    @Autowired
    private CompilationsService compilationsService;

    @Autowired
    private CompilationRepository compilationRepository;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private EventFetchClientAdapter eventFetchClientAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long FIRST_EVENT_ID = 10L;
    private static final long SECOND_EVENT_ID = 20L;

    @AfterEach
    void resetCircuitBreaker() {
        registry.circuitBreaker("event-fetch").reset();
        compilationRepository.deleteAll();
    }

    private NewCompilationDto body(List<Long> events) {
        return NewCompilationDto.builder()
                .title("Летняя подборка")
                .pinned(false)
                .events(events)
                .build();
    }

    private EventShortDto event(long id, String title) {
        return EventShortDto.builder()
                .id(id)
                .title(title)
                .eventDate(LocalDateTime.of(2027, 6, 1, 12, 0, 0))
                .build();
    }

    @Test
    void createCompilation_successSavesCompilationAndLeavesCircuitClosed() {
        List<Long> events = List.of(FIRST_EVENT_ID, SECOND_EVENT_ID);
        when(eventFetchClientAdapter.getEventShortDtos(events))
                .thenReturn(new RemoteCallResult.Success<>(List.of(
                        event(FIRST_EVENT_ID, "Концерт"),
                        event(SECOND_EVENT_ID, "Лекция")
                )));

        CompilationDto result = compilationsService.createCompilation(body(events));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Летняя подборка");
        assertThat(result.isPinned()).isFalse();
        assertThat(result.getEvents())
                .extracting(EventShortDto::getId)
                .containsExactly(FIRST_EVENT_ID, SECOND_EVENT_ID);

        Compilation saved = compilationRepository.findById(result.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Летняя подборка");
        assertThat(saved.isPinned()).isFalse();

        CircuitBreaker cb = registry.circuitBreaker("event-fetch");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void createCompilation_adapterFiltersUnknownEventsAndSavesOnlyConfirmed() {
        List<Long> requested = List.of(FIRST_EVENT_ID, 999L, SECOND_EVENT_ID);
        when(eventFetchClientAdapter.getEventShortDtos(requested))
                .thenReturn(new RemoteCallResult.Success<>(List.of(
                        event(FIRST_EVENT_ID, "Концерт"),
                        event(SECOND_EVENT_ID, "Лекция")
                )));

        CompilationDto result = compilationsService.createCompilation(body(requested));

        assertThat(result.getEvents())
                .extracting(EventShortDto::getId)
                .containsExactly(FIRST_EVENT_ID, SECOND_EVENT_ID);

        List<Long> savedEventIds = jdbcTemplate.queryForList(
                "select event_id from compilation_events where compilation_id = ? order by event_id",
                Long.class,
                result.getId()
        );

        assertThat(savedEventIds)
                .containsExactly(FIRST_EVENT_ID, SECOND_EVENT_ID);
    }

    @Test
    void createCompilation_businessFailurePropagatesAndNothingSaved() {
        when(eventFetchClientAdapter.getEventShortDtos(anyList()))
                .thenReturn(new RemoteCallResult.BusinessFailure<>(400, "bad request"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> compilationsService.createCompilation(body(List.of(FIRST_EVENT_ID))))
                    .isInstanceOf(RemoteCallBusinessException.class)
                    .hasMessageContaining("bad request");
        }

        assertThat(registry.circuitBreaker("event-fetch").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        verify(eventFetchClientAdapter, times(5)).getEventShortDtos(anyList());
        assertThat(compilationRepository.count()).isZero();
    }

    @Test
    void circuitOpensAfterWindowOfTechnicalFailuresAndBlocksSixthCall() {
        when(eventFetchClientAdapter.getEventShortDtos(anyList()))
                .thenReturn(new RemoteCallResult.TechnicalFailure<>("connection refused"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> compilationsService.createCompilation(body(List.of(FIRST_EVENT_ID))))
                    .isInstanceOf(RemoteCallTechnicalException.class)
                    .hasMessage("Сервис событий временно недоступен");
        }

        CircuitBreaker cb = registry.circuitBreaker("event-fetch");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        assertThatThrownBy(() -> compilationsService.createCompilation(body(List.of(FIRST_EVENT_ID))))
                .isInstanceOf(CallNotPermittedException.class);

        verify(eventFetchClientAdapter, times(5)).getEventShortDtos(anyList());
        assertThat(compilationRepository.count()).isZero();
    }
}