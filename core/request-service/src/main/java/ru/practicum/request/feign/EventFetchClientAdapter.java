package ru.practicum.request.feign;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.feign.result.RemoteCallMethods;
import ru.practicum.feign.result.RemoteCallResult;

@Component
@RequiredArgsConstructor
public class EventFetchClientAdapter {
    private final EventFetchClient eventFetchClient;

    public RemoteCallResult<EventDto> getEventByIdAndUserId(long userId, long eventId) {
        try {
            EventDto result = eventFetchClient.getEventByIdAndUserId(userId, eventId);
            return new RemoteCallResult.Success<>(result);
        } catch (FeignException e) {
            return RemoteCallMethods.toFailureResult(e);
        } catch (Exception e) {
            return RemoteCallMethods.toFailureResult(e);
        }
    }

    public RemoteCallResult<EventDto> getEventById(long eventId) {
        try {
            EventDto result = eventFetchClient.getEventById(eventId);
            return new RemoteCallResult.Success<>(result);
        } catch (FeignException e) {
            return RemoteCallMethods.toFailureResult(e);
        } catch (Exception e) {
            return RemoteCallMethods.toFailureResult(e);
        }
    }

    public RemoteCallResult<EventDto> updateConfirmedRequests(long eventId, ConfirmedRequestsUpdate confirmedRequests) {
        try {
            EventDto result = eventFetchClient.updateConfirmedRequests(eventId, confirmedRequests);
            return new RemoteCallResult.Success<>(result);
        } catch (FeignException e) {
            return RemoteCallMethods.toFailureResult(e);
        } catch (Exception e) {
            return RemoteCallMethods.toFailureResult(e);
        }
    }
}
