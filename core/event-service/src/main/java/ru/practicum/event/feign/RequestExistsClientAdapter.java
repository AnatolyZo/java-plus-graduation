package ru.practicum.event.feign;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.feign.result.RemoteCallMethods;
import ru.practicum.feign.result.RemoteCallResult;

@Component
@RequiredArgsConstructor
public class RequestExistsClientAdapter {
    private final RequestExistsClient requestExistsClient;

    public RemoteCallResult<Boolean> checkExistenceByEventIdAndRequesterId(long eventId, long userId) {
        try {
            Boolean result = requestExistsClient.checkExistenceByEventIdAndRequesterId(eventId, userId);
            return new RemoteCallResult.Success<>(result);
        } catch (FeignException e) {
            return RemoteCallMethods.toFailureResult(e);
        } catch (Exception e) {
            return RemoteCallMethods.toFailureResult(e);
        }
    }
}
