package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.common.ControllerConstants;

import java.util.List;
import java.util.Set;

@FeignClient(name = "event-service", contextId = "event-exists-client")
public interface EventExistsClient {

    @GetMapping(ControllerConstants.ACCESS_ADMIN + ControllerConstants.URL_EVENTS + ControllerConstants.URL_INTERNAL + ControllerConstants.URL_EXISTS)
    Set<Long> checkExists(@RequestParam("ids") List<Long> ids);

    @GetMapping(ControllerConstants.ACCESS_ADMIN + ControllerConstants.URL_EVENTS + ControllerConstants.URL_INTERNAL + ControllerConstants.URL_EXISTS + "/{" + ControllerConstants.ID_EVENT + "}/{" + ControllerConstants.ID_USER + "}")
    Boolean checkExistenceWithInitiatorId(@PathVariable(ControllerConstants.ID_EVENT) long eventId,
                                          @PathVariable(ControllerConstants.ID_USER) long initiatorId);
}
