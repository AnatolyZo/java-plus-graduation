package ru.practicum.request.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.request.service.RequestService;

import static ru.practicum.common.ControllerConstants.*;

@RestController
@RequestMapping(path = ACCESS_ADMIN + URL_REQUESTS)
@RequiredArgsConstructor
public class AdminRequestController {
    private final RequestService service;

    @GetMapping(URL_INTERNAL + URL_EXISTS + "/{" + ID_EVENT + "}/{" + ID_USER + "}")
    public ResponseEntity<Boolean> checkExistenceByEventIdAndRequesterId(@PathVariable(ID_EVENT) @NotNull @Positive Long eventId,
                                                                         @PathVariable(ID_USER) @NotNull @Positive Long requesterId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(service.checkExistenceByEventIdAndRequesterId(eventId, requesterId));
    }
}
