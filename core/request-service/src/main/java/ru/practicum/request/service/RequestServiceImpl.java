package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.common.Entities;
import ru.practicum.feign.exception.EventServiceInteractionException;
import ru.practicum.feign.result.RemoteCallResult;
import ru.practicum.request.feign.*;
import ru.practicum.request.dto.ChangedRequestStatusesDto;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.request.dto.RequestStatus;
import ru.practicum.request.dto.UpdateRequestStatusDto;
import ru.practicum.request.entity.Request;
import ru.practicum.request.exception.DuplicatedDataException;
import ru.practicum.request.exception.NotFoundException;
import ru.practicum.request.exception.UnavailableUpdateException;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.service.support.RequestCheckService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RequestServiceImpl implements RequestService {
    private final RequestRepository requestRepository;
    private final EventFetchClientAdapter eventFetchClientAdapter;
    private final RequestCheckService requestCheckService;

    @Override
    public List<RequestDto> getRequests(long userId, long eventId) {
        log.trace("Инициировано получение запроса пользователем с id {} к событию с id {}", userId, eventId);
        requestCheckService.checkEventExistence(userId, eventId);
        return getRequestsToUsersEvent(eventId);
    }

    @Override
    @Transactional
    public ChangedRequestStatusesDto updateRequestStatuses(long userId, long eventId, UpdateRequestStatusDto update) {
        log.trace("Инициировано обновление статусов запросов пользователем с id {} к событию с id {}, изменения - {}", userId, eventId, update);
        EventDto event = getEventByIdUserId(userId, eventId);
        //Обработка ситуации, когда не установлено ограничение по количеству участников
        //или запрос не требует модерации
        if ((event.participantLimit() == 0 || !event.requestModeration()) && update.getStatus().equals(RequestStatus.CONFIRMED)) {
            return confirmRequestsIfNoDemandsMade(event, update);
        }

        int requestsAvailableToConfirm = event.participantLimit() - event.confirmedRequests();

        if (requestsAvailableToConfirm == 0) {
            throw new UnavailableUpdateException(Entities.EVENT.name(), eventId);
        }

        //Обработка случая подтверждения запросов
        if (update.getStatus().equals(RequestStatus.CONFIRMED)) {
            return processRequestsWithActionStatusConfirmed(event, update, requestsAvailableToConfirm);
        }
        //Обработка случая отклонения запросов
        return processRequestsWithActionStatusRejected(update);
    }

    @Override
    @Transactional
    public RequestDto create(long userId, long eventId) {
        log.trace("Инициировано создание запроса у пользователя с id {} для события с id {}", userId, eventId);
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new DuplicatedDataException(Entities.REQUEST.name(), "eventId and userId", eventId);
        }

        EventDto event = getEventById(eventId);
        requestCheckService.requestChecks(event, userId);
        requestCheckService.checkUserExistence(userId);
        RequestStatus status = changeRequestStatus(event);

        Request request = Request.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(userId)
                .status(status)
                .build();

        Request createdRequest = requestRepository.save(request);
        incrementEventConfirmedRequests(createdRequest, event);
        log.debug("Создан запрос {}", createdRequest);
        return RequestDto.builder()
                .id(createdRequest.getId())
                .created(createdRequest.getCreated())
                .event(request.getEventId())
                .requester(request.getRequesterId())
                .status(createdRequest.getStatus())
                .build();
    }


    @Override
    @Transactional
    public RequestDto cancelRequest(long userId, long requestId) {
        log.trace("Инициирована отмена запроса с id {} у пользователя с id {}", userId, requestId);
        Request request = findRequestById(requestId);

        if (request.getStatus().equals(RequestStatus.CONFIRMED)) {
            throw new DuplicatedDataException(Entities.REQUEST.name(), "status", RequestStatus.CONFIRMED);
        }

        request.setStatus(RequestStatus.CANCELED);
        Request updatedRequest = requestRepository.save(request);
        log.debug("Обновлен запрос {}", updatedRequest);
        return RequestDto.builder()
                .id(updatedRequest.getId())
                .created(updatedRequest.getCreated())
                .event(request.getEventId())
                .requester(request.getRequesterId())
                .status(updatedRequest.getStatus())
                .build();
    }

    @Override
    public List<RequestDto> getUserRequests(long requesterId) {
        log.trace("Инициировано получение запросов пользователя с id {}", requesterId);
        List<Request> requests = requestRepository.findByRequesterIdOrderByCreatedDesc(requesterId);
        log.debug("Получены запросы пользователя {}", requests);
        return requests.stream()
                .map(request -> RequestDto.builder()
                        .id(request.getId())
                        .created(request.getCreated())
                        .event(request.getEventId())
                        .requester(request.getRequesterId())
                        .status(request.getStatus())
                        .build())
                .toList();
    }

    private EventDto getEventByIdUserId(long userId, long eventId) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.getEventByIdAndUserId(userId, eventId);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                if (bf.status() == 404) {
                    log.warn("Событие не найдено: {}", bf.message());
                    throw new NotFoundException(Entities.EVENT, eventId);
                }

                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при получении события: " + tf.reason());
            }
        };
    }

    private EventDto getEventById(long eventId) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.getEventById(eventId);

        return switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                if (bf.status() == 404) {
                    log.warn("Событие не найдено: {}", bf.message());
                    throw new NotFoundException(Entities.EVENT, eventId);
                }

                log.warn("Бизнес-отказ при получении события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при получении события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при получении события: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при получении события: " + tf.reason());
            }
        };
    }

    private List<RequestDto> getRequestsToUsersEvent(long eventId) {
        log.trace("Инициировано получение запросов к событию с id {}", eventId);
        List<Request> requests = requestRepository.findByEventId(eventId);
        log.debug("Получен список запросов {}", requests);
        return requests.stream()
                .map(request -> RequestDto.builder()
                        .id(request.getId())
                        .created(request.getCreated())
                        .event(request.getEventId())
                        .requester(request.getRequesterId())
                        .status(request.getStatus())
                        .build())
                .toList();
    }

    private List<RequestDto> changeRequestStatuses(List<Long> requestIds, RequestStatus status) {
        log.trace("Инициировано изменение статусов на {} к списку запросов {}", status, requestIds);
        List<Request> requests = requestRepository.findByIdIn(requestIds);
        Optional<Long> nonPendingRequestId = requests.stream()
                .filter(request -> !request.getStatus().equals(RequestStatus.PENDING))
                .findFirst()
                .map(Request::getId);

        if (nonPendingRequestId.isPresent()) {
            throw new UnavailableUpdateException(Entities.REQUEST.name(), nonPendingRequestId.get());
        }

        requests.forEach(request -> request.setStatus(status));
        log.debug("Изменен статус у запросов {}", requests);
        return requests.stream()
                .map(request -> RequestDto.builder()
                        .id(request.getId())
                        .created(request.getCreated())
                        .event(request.getEventId())
                        .requester(request.getRequesterId())
                        .status(request.getStatus())
                        .build())
                .toList();
    }

    public List<RequestDto> runRequestStatusesConfirmationProcedure(List<Long> requestIds, RequestStatus status) {
        log.trace("Инициировано изменение статусов на {} к списку запросов {}", status, requestIds);
        List<Request> requests = requestRepository.findByIdIn(requestIds);
        Optional<Long> nonPendingRequestId = requests.stream()
                .filter(request -> !request.getStatus().equals(RequestStatus.PENDING))
                .findFirst()
                .map(Request::getId);

        if (nonPendingRequestId.isPresent()) {
            throw new UnavailableUpdateException(Entities.REQUEST.name(), nonPendingRequestId.get());
        }

        requests.forEach(request -> request.setStatus(status));
        log.debug("Изменен статус у запросов {}", requests);
        return requests.stream()
                .map(request -> RequestDto.builder()
                        .id(request.getId())
                        .created(request.getCreated())
                        .event(request.getEventId())
                        .requester(request.getRequesterId())
                        .status(request.getStatus())
                        .build())
                .toList();
    }

    private ChangedRequestStatusesDto confirmRequestsIfNoDemandsMade(EventDto event, UpdateRequestStatusDto update) {
        List<Long> requestIds = update.getRequestIds();
        return runRequestStatusesConfirmationProcedure(event, requestIds, List.of());
    }

    private ChangedRequestStatusesDto processRequestsWithActionStatusConfirmed(EventDto event,
                                                                               UpdateRequestStatusDto update,
                                                                               int requestsAvailableToConfirm) {
        //Разделяем запросы на те, которые можем одобрить и отклонить, исходя из количества доступных мест
        List<Long> requestsToConfirm = update.getRequestIds().stream()
                .limit(requestsAvailableToConfirm)
                .toList();

        List<Long> requestsToReject = update.getRequestIds().stream()
                .skip(requestsAvailableToConfirm)
                .toList();

        return runRequestStatusesConfirmationProcedure(event, requestsToConfirm, requestsToReject);
    }

    private ChangedRequestStatusesDto runRequestStatusesConfirmationProcedure(EventDto event, List<Long> requestsToConfirm, List<Long> requestsToReject) {
        List<RequestDto> confirmedRequests;
        if (!requestsToConfirm.isEmpty()) {
            confirmedRequests = changeRequestStatuses(requestsToConfirm, RequestStatus.CONFIRMED);
        } else {
            confirmedRequests = List.of();
        }

        List<RequestDto> rejectedRequests;
        if (!requestsToReject.isEmpty()) {
            rejectedRequests = changeRequestStatuses(requestsToReject, RequestStatus.REJECTED);
        } else {
            rejectedRequests = List.of();
        }

        //Обновляем количество свободных мест
        int updatedConfirmedRequests = event.confirmedRequests() + confirmedRequests.size();
        ConfirmedRequestsUpdate confirmedRequestsUpdate = new ConfirmedRequestsUpdate(updatedConfirmedRequests);

        updateConfirmedRequestsWithFallback(event.id(), confirmedRequestsUpdate);

        log.debug("Обновлены статусы запросов: подтвержденные - {}, отклоненные - {}", confirmedRequests, rejectedRequests);
        return ChangedRequestStatusesDto.builder()
                .confirmedRequests(confirmedRequests)
                .rejectedRequests(rejectedRequests)
                .build();
    }

    private ChangedRequestStatusesDto processRequestsWithActionStatusRejected(UpdateRequestStatusDto update) {
        List<RequestDto> rejectedRequests = runRequestStatusesConfirmationProcedure(update.getRequestIds(), RequestStatus.REJECTED);

        return ChangedRequestStatusesDto.builder()
                .confirmedRequests(List.of())
                .rejectedRequests(rejectedRequests)
                .build();
    }

    private RequestStatus changeRequestStatus(EventDto event) {
        RequestStatus status;

        if (event.participantLimit() == 0 || !event.requestModeration()) {
            status = RequestStatus.CONFIRMED;
        } else {
            status = RequestStatus.PENDING;
        }

        return status;
    }

    private void incrementEventConfirmedRequests(Request request, EventDto event) {
        if (request.getStatus().equals(RequestStatus.CONFIRMED)) {
            int confirmedRequests = event.confirmedRequests();
            ConfirmedRequestsUpdate confirmedRequestsUpdate = new ConfirmedRequestsUpdate(++confirmedRequests);
            updateConfirmedRequestsWithFallback(event.id(), confirmedRequestsUpdate);
        }
    }

    private Request findRequestById(long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(Entities.REQUEST, requestId));
    }

    private void updateConfirmedRequestsWithFallback(long eventId, ConfirmedRequestsUpdate confirmedRequestsUpdate) {
        RemoteCallResult<EventDto> result = eventFetchClientAdapter.updateConfirmedRequests(eventId, confirmedRequestsUpdate);

        switch (result) {
            case RemoteCallResult.Success<EventDto> s -> s.value();
            case RemoteCallResult.BusinessFailure<?> bf -> {
                log.warn("Бизнес-отказ при обновлении доступных мест события: {}", bf.message());
                throw new EventServiceInteractionException("Проверьте корректность передаваемых данных, возникла ошибка при обновлении доступных мест события: " + bf.message());
            }
            case RemoteCallResult.TechnicalFailure<?> tf -> {
                log.warn("Технический сбой при обновлении доступных мест события: {}", tf.reason());
                throw new EventServiceInteractionException("Технический сбой при обновлении доступных мест события: " + tf.reason());
            }
        }
    }
}
