package ru.practicum.compilation.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static ru.practicum.common.ControllerConstants.*;

@FeignClient(name = "event-service", contextId = "compilation-event-client")
public interface EventFetchClient {

    @GetMapping(ACCESS_ADMIN + URL_EVENTS + URL_INTERNAL)
    List<EventShortDto> getEventShortDtos(@RequestParam("ids") List<Long> ids);
}
