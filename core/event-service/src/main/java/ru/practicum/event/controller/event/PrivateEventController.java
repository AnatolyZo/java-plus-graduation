package ru.practicum.event.controller.event;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.event.EventDto;
import ru.practicum.event.dto.event.NewEventDto;
import ru.practicum.event.dto.event.UserUpdateEventDto;
import ru.practicum.event.service.EventService;

import java.util.List;

import static ru.practicum.common.ControllerConstants.*;

@RestController
@RequestMapping(path = ACCESS_PRIVATE + "/{" + ID_USER + "}" + URL_EVENTS)
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class PrivateEventController {
    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventDto>> getEvents(
            @PathVariable(name = ID_USER) long userId,
            @RequestParam(name = PARAM_FROM, defaultValue = "0") int from,
            @RequestParam(name = PARAM_SIZE, defaultValue = "10") int size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.getEvents(userId, from, size));
    }

    @PostMapping
    public ResponseEntity<EventDto> createEvent(
            @PathVariable(name = ID_USER) long userId,
            @Valid @RequestBody NewEventDto newEventDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(userId, newEventDto));
    }

    @GetMapping("/{" + ID_EVENT + "}")
    public ResponseEntity<EventDto> getEvent(
            @PathVariable(name = ID_USER) long userId,
            @PathVariable(name = ID_EVENT) long eventId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.getEvent(userId, eventId));
    }

    @PatchMapping("/{" + ID_EVENT + "}")
    public ResponseEntity<EventDto> updateEvent(
            @PathVariable(name = ID_USER) long userId,
            @PathVariable(name = ID_EVENT) long eventId,
            @Valid @RequestBody UserUpdateEventDto userUpdateEventDto
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.updateEvent(userId, eventId, userUpdateEventDto));
    }
}
