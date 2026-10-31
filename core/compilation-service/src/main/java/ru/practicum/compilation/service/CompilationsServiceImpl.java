package ru.practicum.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.Entities;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;
import ru.practicum.compilation.entity.Compilation;
import ru.practicum.compilation.entity.CompilationEvent;
import ru.practicum.compilation.exception.NotFoundException;
import ru.practicum.compilation.feign.EventFetchClientAdapter;
import ru.practicum.compilation.feign.EventShortDto;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationsServiceImpl implements CompilationsService {
    private final CompilationRepository compilationRepository;
    private final EventFetchClientAdapter eventFetchClientAdapter;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto body) {
        log.trace("Инициировано сохранение подборки. Тело запроса: {}", body);
        List<Long> events = body.getEvents();
        List<EventShortDto> eventShortDtoList = getEventShortDtosWithFallback(events);
        //Получаем новый список id событий, так как не все моглибыть найдены по результатам предыдущей операции
        List<Long> eventIds = eventShortDtoList.stream()
                .map(EventShortDto::getId)
                .toList();
        log.trace("Найдено {} событий", eventIds.size());
        Compilation compilation = CompilationMapper.toCompilation(body);
        linkEvents(compilation, eventIds);
        Compilation result = compilationRepository.save(compilation);
        log.debug("Подборка {} сохранена", compilation);
        return composeComplicationResponse(result, eventShortDtoList);
    }

    private void linkEvents(Compilation compilation, List<Long> events) {
        events.forEach(event -> {
                    CompilationEvent compilationEvent = CompilationEvent.builder()
                            .compilation(compilation)
                            .eventId(event)
                            .build();
                    compilation.getEvents().add(compilationEvent);
                }
        );
    }

    private CompilationDto composeComplicationResponse(Compilation compilation,
                                                       List<EventShortDto> eventShortDtoList) {
        CompilationDto compilationDto = CompilationMapper.toCompilationDto(compilation);
        compilationDto.setEvents(eventShortDtoList);
        return compilationDto;
    }

    @Override
    @Transactional
    public void deleteCompilation(long compId) {
        log.trace("Инициировано удаление подборки с id={}", compId);
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException(Entities.COMPILATION, compId);
        }
        compilationRepository.deleteById(compId);
        log.debug("Подборка с id={} удалена", compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(long compId, UpdateCompilationRequest body) {
        log.trace("Инициировано обновление сборки с id={}. Тело запроса: {}", compId, body);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(Entities.COMPILATION, compId));
        log.trace("Подборка найдена: {}", compilation);
        List<Long> events = body.getEvents();
        List<EventShortDto> eventShortDtoList = getEventShortDtosWithFallback(events);
        List<Long> eventIds = eventShortDtoList.stream()
                .map(EventShortDto::getId)
                .toList();
        Compilation update = updateCompilationData(compilation, eventIds, body);
        log.debug("Создано обновление подборки: {}", update);
        Compilation result = compilationRepository.save(update);
        log.debug("Обновление {} сохранено", update);
        return composeComplicationResponse(result, eventShortDtoList);
    }

    private Compilation updateCompilationData(Compilation compilation,
                                              List<Long> eventIdsUpdate,
                                              UpdateCompilationRequest update) {
        if (eventIdsUpdate != null) {
            linkEvents(compilation, eventIdsUpdate);
        }

        Boolean pinnedUpdate = update.getPinned();
        if (pinnedUpdate != null) {
            compilation.setPinned(pinnedUpdate);
        }

        String titleUpdate = update.getTitle();
        if (titleUpdate != null) {
            compilation.setTitle(titleUpdate);
        }
        return compilation;
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        log.trace("Инициировано получение подборок с параметрами pinned={}, from={} и size={}", pinned, from, size);
        List<Compilation> compilations = compilationRepository.findWithOffset(pinned, from, size);

        if (compilations.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventShortDto> allEvents = getAllEvents(compilations);
        Map<Long, EventShortDto> eventsMap = allEvents.stream()
                .collect(Collectors.toMap(
                        EventShortDto::getId,
                        Function.identity()
                ));

        log.debug("Найдено {} подборок", compilations.size());
        return compilations.stream()
                .map(compilation -> buildCompilationDto(compilation, eventsMap))
                .toList();
    }

    private List<EventShortDto> getAllEvents(List<Compilation> compilations) {
        //Сбор всех id событий для получения одним запросом
        List<Long> allEventIds = compilations.stream()
                .flatMap(compilation -> compilation.getEvents().stream())
                .map(CompilationEvent::getEventId)
                .toList();
        return getEventShortDtosWithFallback(allEventIds);
    }

    private CompilationDto buildCompilationDto(Compilation compilation, Map<Long, EventShortDto> eventsMap) {
        List<Long> eventIds = compilation.getEvents().stream()
                .map(CompilationEvent::getEventId)
                .toList();

        List<EventShortDto> events = eventIds.stream()
                .map(eventsMap::get)
                .filter(Objects::nonNull)
                .toList();

        return composeComplicationResponse(compilation, events);
    }

    @Override
    public CompilationDto getCompilation(long compId) {
        log.trace("Инициировано получение подборки с id={}", compId);
        Compilation result = compilationRepository.findWithEventsById(compId)
                .orElseThrow(() -> new NotFoundException(Entities.COMPILATION, compId));
        log.debug("Найдена подборка {}", result);
        List<Long> eventIds = result.getEvents().stream()
                .map(CompilationEvent::getEventId)
                .toList();
        List<EventShortDto> eventShortDtoList = getEventShortDtosWithFallback(eventIds);
        return composeComplicationResponse(result, eventShortDtoList);
    }

    private List<EventShortDto> getEventShortDtosWithFallback(List<Long> eventIds) {
        RemoteCallResult<List<EventShortDto>> result = eventFetchClientAdapter.getEventShortDtos(eventIds);

        return switch (result) {
            case RemoteCallResult.Success<List<EventShortDto>> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при получении событий: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении событий: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении событий: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при получении событий: " + tf.reason());
            }
        };
    }
}
