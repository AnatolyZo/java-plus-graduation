package ru.practicum.event.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import static ru.practicum.common.ControllerConstants.*;

@FeignClient(name = "request-service", contextId = "request-exists-client")
public interface RequestExistsClient {

    @GetMapping(ACCESS_ADMIN + URL_REQUESTS + URL_INTERNAL + URL_EXISTS + "/{" + ID_EVENT + "}/{" + ID_USER + "}")
    Boolean checkExistenceByEventIdAndRequesterId(@PathVariable(ID_EVENT) long eventId,
                                                  @PathVariable(ID_USER) long initiatorId);
}
