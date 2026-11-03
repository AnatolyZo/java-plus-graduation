package ru.practicum.feign.result;

import feign.FeignException;

public class RemoteCallMethods {
    private RemoteCallMethods() {
    }

    public static <T> RemoteCallResult<T> toFailureResult(FeignException e) {
        int status = e.status();

        if (status >= 400 && status < 500) {
            return new RemoteCallResult.BusinessFailure<>(e.status(), e.getMessage());
        }
        return new RemoteCallResult.TechnicalFailure<>(e.getMessage());
    }

    public static <T> RemoteCallResult<T> toFailureResult(Exception e) {
        return new RemoteCallResult.TechnicalFailure<>(e.getMessage());
    }
}