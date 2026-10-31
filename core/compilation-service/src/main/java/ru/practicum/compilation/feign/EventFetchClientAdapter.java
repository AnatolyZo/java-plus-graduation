package ru.practicum.compilation.feign;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.feign.result.RemoteCallMethods;
import ru.practicum.feign.result.RemoteCallResult;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EventFetchClientAdapter {
    private final EventFetchClient eventFetchClient;

    public RemoteCallResult<List<EventShortDto>> getEventShortDtos(List<Long> ids) {
        try {
            List<EventShortDto> result = eventFetchClient.getEventShortDtos(ids);
            return new RemoteCallResult.Success<>(result);
        } catch (FeignException e) {
            return RemoteCallMethods.toFailureResult(e);
        } catch (Exception e) {
            return RemoteCallMethods.toFailureResult(e);
        }
    }
}
