package ru.practicum.request.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import static ru.practicum.common.ControllerConstants.*;

@FeignClient(name = "event-service", contextId = "request-event-client")
public interface EventFetchClient {

    @GetMapping(ACCESS_PRIVATE + "/{" + ID_USER + "}" + URL_EVENTS + "/{" + ID_EVENT + "}")
    EventDto getEventByIdAndUserId(@PathVariable(name = ID_USER) long userId, @PathVariable(name = ID_EVENT) long eventId);

    @GetMapping(ACCESS_ADMIN + URL_EVENTS + URL_INTERNAL + "/{" + ID_EVENT + "}")
    EventDto getEventById(@PathVariable(ID_EVENT) long eventId);

    @PutMapping(ACCESS_ADMIN + URL_EVENTS + URL_INTERNAL + "/{" + ID_EVENT + "}")
    EventDto updateConfirmedRequests(@PathVariable(ID_EVENT) long eventId,
                                     @RequestBody ConfirmedRequestsUpdate confirmedRequests);
}
