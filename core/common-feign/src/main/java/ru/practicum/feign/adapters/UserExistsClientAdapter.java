package ru.practicum.feign.adapters;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.feign.UserExistsClient;
import ru.practicum.feign.result.RemoteCallMethods;
import ru.practicum.feign.result.RemoteCallResult;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserExistsClientAdapter {
    private final UserExistsClient userExistsClient;

    public RemoteCallResult<Set<Long>> checkExists(List<Long> ids) {
        try {
            Set<Long> result = userExistsClient.checkExists(ids);
            return new RemoteCallResult.Success<>(result);
        } catch (FeignException e) {
            return RemoteCallMethods.toFailureResult(e);
        } catch (Exception e) {
            return RemoteCallMethods.toFailureResult(e);
        }
    }
}
