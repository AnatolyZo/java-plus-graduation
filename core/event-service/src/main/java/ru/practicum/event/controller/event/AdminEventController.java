package ru.practicum.event.controller.event;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.event.AdminUpdateEventDto;
import ru.practicum.event.dto.event.ConfirmedRequestsUpdate;
import ru.practicum.event.dto.event.EventDto;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.service.EventService;

import java.util.List;
import java.util.Set;

import static ru.practicum.common.ControllerConstants.*;

@RestController
@RequestMapping(path = ACCESS_ADMIN + URL_EVENTS)
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class AdminEventController {
    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventDto>> searchEvents(
            @RequestParam(required = false) List<Long> users,
            @RequestParam(required = false) List<String> states,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) String rangeStart,
            @RequestParam(required = false) String rangeEnd,
            @RequestParam(name = PARAM_FROM, defaultValue = "0") int from,
            @RequestParam(name = PARAM_SIZE, defaultValue = "10") int size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.searchEvents(users, states, categories, rangeStart, rangeEnd, from, size));
    }

    @PatchMapping("/{" + ID_EVENT + "}")
    public ResponseEntity<EventDto> updateEvent(
            @PathVariable(name = ID_EVENT) long eventId,
            @Valid @RequestBody AdminUpdateEventDto adminUpdateEventDto
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.updateEvent(eventId, adminUpdateEventDto));
    }

    @GetMapping(URL_INTERNAL + URL_EXISTS)
    public ResponseEntity<Set<Long>> checkExists(@RequestParam(value = "ids", required = false) List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(Set.of());
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.getEventsIds(ids));
    }

    @GetMapping(URL_INTERNAL + URL_EXISTS + "/{" + ID_EVENT + "}/{" + ID_USER + "}")
    public ResponseEntity<Boolean> checkExistenceWithInitiatorId(@PathVariable(ID_EVENT) long eventId,
                                                                 @PathVariable(ID_USER) long initiatorId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.checkExistenceWithInitiatorId(eventId, initiatorId));
    }

    @GetMapping(URL_INTERNAL + "/{" + ID_EVENT + "}")
    public ResponseEntity<EventDto> getEventById(@PathVariable(ID_EVENT) long eventId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.getEventById(eventId));
    }

    @PutMapping(URL_INTERNAL + "/{" + ID_EVENT + "}")
    public ResponseEntity<EventDto> updateConfirmedRequests(@PathVariable(ID_EVENT) long eventId,
                                                            @Valid @RequestBody ConfirmedRequestsUpdate confirmedRequests) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.updateConfirmedRequests(eventId, confirmedRequests));
    }

    @GetMapping(URL_INTERNAL)
    public ResponseEntity<List<EventShortDto>> getEventShortDtos(@RequestParam(value = "ids", required = false) List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.getEventShortDtos(ids));
    }
}
