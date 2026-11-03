package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.common.ControllerConstants;

import java.util.List;
import java.util.Set;

@FeignClient(name = "user-service", contextId = "user-exists-client")
public interface UserExistsClient {

    @GetMapping(ControllerConstants.ACCESS_ADMIN + ControllerConstants.URL_USERS + ControllerConstants.URL_INTERNAL + ControllerConstants.URL_EXISTS)
    Set<Long> checkExists(@RequestParam("ids") List<Long> ids);
}
