package ru.practicum.request.service;


import ru.practicum.request.dto.ChangedRequestStatusesDto;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.request.dto.UpdateRequestStatusDto;

import java.util.List;

public interface RequestService {
    List<RequestDto> getRequests(long userId, long eventId);

    ChangedRequestStatusesDto updateRequestStatuses(long userId, long eventId, UpdateRequestStatusDto update);

    RequestDto create(long userId, long eventId);

    RequestDto cancelRequest(long userId, long requestId);

    List<RequestDto> getUserRequests(long requesterId);
}
