package ru.practicum.request.controller;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.ChangedRequestStatusesDto;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.request.dto.UpdateRequestStatusDto;
import ru.practicum.request.service.RequestService;

import java.util.List;

import static ru.practicum.common.ControllerConstants.*;

@RestController
@RequestMapping(path = ACCESS_PRIVATE + "/{" + ID_USER + "}")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PrivateRequestController {
    private final RequestService service;

    @GetMapping(URL_EVENTS + "/{" + ID_EVENT + "}" + URL_REQUESTS)
    public ResponseEntity<List<RequestDto>> getRequests(
            @PathVariable(name = ID_USER) long userId,
            @PathVariable(name = ID_EVENT) long eventId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(service.getRequests(userId, eventId));
    }

    @PatchMapping(URL_EVENTS + "/{" + ID_EVENT + "}" + URL_REQUESTS)
    public ResponseEntity<ChangedRequestStatusesDto> updateRequestStatuses(
            @PathVariable(name = ID_USER) long userId,
            @PathVariable(name = ID_EVENT) long eventId,
            @Valid @RequestBody UpdateRequestStatusDto update
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(service.updateRequestStatuses(userId, eventId, update));
    }

    @PostMapping(URL_REQUESTS)
    public ResponseEntity<RequestDto> addRequest(@PathVariable @NotNull @Positive Long userId,
                                                 @RequestParam(name = "eventId") @NotNull @Positive Long eventId) {
        RequestDto createdRequest = service.create(userId, eventId);
        log.info("Создан запрос на участие с именем {}.", createdRequest.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createdRequest);
    }

    @GetMapping(URL_REQUESTS)
    public List<RequestDto> getUserRequests(@PathVariable @NotNull @Positive Long userId) {
        return service.getUserRequests(userId);
    }

    @PatchMapping(URL_REQUESTS + "/{" + ID_REQUEST + "}" + URL_CANCEL)
    public ResponseEntity<RequestDto> cancelRequest(@PathVariable @NotNull @Positive Long userId,
                                                    @PathVariable @NotNull @Positive Long requestId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(service.cancelRequest(userId, requestId));
    }

}
