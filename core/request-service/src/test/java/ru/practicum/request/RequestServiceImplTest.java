package ru.practicum.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.common.Entities;
import ru.practicum.feign.result.RemoteCallResult;
import ru.practicum.request.dto.ChangedRequestStatusesDto;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.request.dto.RequestStatus;
import ru.practicum.request.dto.UpdateRequestStatusDto;
import ru.practicum.request.entity.Request;
import ru.practicum.request.exception.DuplicatedDataException;
import ru.practicum.request.exception.NotFoundException;
import ru.practicum.request.exception.UnavailableUpdateException;
import ru.practicum.request.feign.*;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.service.RequestServiceImpl;
import ru.practicum.request.service.support.RequestCheckService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RequestServiceImplTest {
    @Mock
    private RequestRepository requestRepository;

    @Mock
    private EventFetchClientAdapter eventFetchClientAdapter;

    @Mock
    private RequestCheckService requestCheckService;

    @InjectMocks
    private RequestServiceImpl requestService;

    private static final long EVENT_ID = 1L;
    private static final long USER_ID = 2L;
    private static final long REQUEST_ID = 3L;

    @Test
    void create_success() {
        EventDto event = new EventDto(EVENT_ID, 2, 99L, 10, false, EventStatus.PUBLISHED);
        EventDto createdEvent = new EventDto(EVENT_ID, 3, 99L, 10, false, EventStatus.PUBLISHED);

        Request savedRequest = new Request();
        savedRequest.setId(REQUEST_ID);
        savedRequest.setCreated(LocalDateTime.now());
        savedRequest.setEventId(EVENT_ID);
        savedRequest.setRequesterId(USER_ID);
        savedRequest.setStatus(RequestStatus.CONFIRMED);

        when(eventFetchClientAdapter.getEventById(anyLong())).thenReturn(new RemoteCallResult.Success<>(event));
        when(requestRepository.existsByEventIdAndRequesterId(anyLong(), anyLong())).thenReturn(false);
        doNothing().when(requestCheckService).checkUserExistence(USER_ID);
        when(requestRepository.save(any(Request.class))).thenReturn(savedRequest);
        when(eventFetchClientAdapter.updateConfirmedRequests(anyLong(), any(ConfirmedRequestsUpdate.class))).thenReturn(new RemoteCallResult.Success<>(createdEvent));

        RequestDto dto = requestService.create(USER_ID, EVENT_ID);

        assertThat(dto.getId()).isEqualTo(REQUEST_ID);
        assertThat(dto.getStatus()).isEqualTo(RequestStatus.CONFIRMED);
        verify(requestRepository, times(1)).existsByEventIdAndRequesterId(EVENT_ID, USER_ID);
        verify(eventFetchClientAdapter, times(1)).getEventById(EVENT_ID);
        verify(requestCheckService, times(1)).checkUserExistence((USER_ID));
        verify(requestRepository, times(1)).save(any(Request.class));
        verify(eventFetchClientAdapter, times(1)).updateConfirmedRequests(EVENT_ID, new ConfirmedRequestsUpdate(3));
    }

    @Test
    void create_duplicateRequest_throws() {
        when(requestRepository.existsByEventIdAndRequesterId(anyLong(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> requestService.create(USER_ID, EVENT_ID))
                .isInstanceOf(DuplicatedDataException.class)
                .hasMessageContaining(Entities.REQUEST.name());

        verify(requestRepository, times(1)).existsByEventIdAndRequesterId(EVENT_ID, USER_ID);
        verify(eventFetchClientAdapter, never()).getEventById(anyLong());
        verify(requestCheckService, never()).checkUserExistence(USER_ID);
    }

    @Test
    void create_unpublishedEvent_throws() {
        EventDto event = new EventDto(EVENT_ID, 0, 99L, 5, true, EventStatus.PENDING);

        when(eventFetchClientAdapter.getEventById(anyLong())).thenReturn(new RemoteCallResult.Success<>(event));
        when(requestRepository.existsByEventIdAndRequesterId(anyLong(), anyLong())).thenReturn(false);

        doThrow(new UnavailableUpdateException(Entities.EVENT.name(), EVENT_ID))
                .when(requestCheckService)
                .requestChecks(event, USER_ID);

        assertThatThrownBy(() -> requestService.create(USER_ID, EVENT_ID))
                .isInstanceOf(UnavailableUpdateException.class);

        verify(requestRepository, times(1)).existsByEventIdAndRequesterId(EVENT_ID, USER_ID);
        verify(eventFetchClientAdapter, times(1)).getEventById(EVENT_ID);
    }

    @Test
    void create_eventLimitReached_throws() {
        EventDto event = new EventDto(EVENT_ID, 5, 99L, 5, true, EventStatus.PUBLISHED);

        when(eventFetchClientAdapter.getEventById(anyLong())).thenReturn(new RemoteCallResult.Success<>(event));
        when(requestRepository.existsByEventIdAndRequesterId(anyLong(), anyLong())).thenReturn(false);

        doThrow(new UnavailableUpdateException(Entities.EVENT.name(), EVENT_ID))
                .when(requestCheckService)
                .requestChecks(event, USER_ID);

        assertThatThrownBy(() -> requestService.create(USER_ID, EVENT_ID))
                .isInstanceOf(UnavailableUpdateException.class);

        verify(requestRepository, times(1)).existsByEventIdAndRequesterId(EVENT_ID, USER_ID);
        verify(eventFetchClientAdapter, times(1)).getEventById(EVENT_ID);
    }

    @Test
    void cancelRequest_confirmedStatus_throws() {
        Request request = new Request();
        request.setId(REQUEST_ID);
        request.setStatus(RequestStatus.CONFIRMED);

        when(requestRepository.findById(anyLong())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.cancelRequest(USER_ID, REQUEST_ID))
                .isInstanceOf(DuplicatedDataException.class)
                .hasMessageContaining("status");

        verify(requestRepository, times(1)).findById(REQUEST_ID);
        verify(requestRepository, never()).save(any(Request.class));
    }

    @Test
    void cancelRequest_success() {
        Request request = new Request();
        request.setId(REQUEST_ID);
        request.setStatus(RequestStatus.PENDING);
        request.setEventId(EVENT_ID);
        request.setRequesterId(USER_ID);

        when(requestRepository.findById(anyLong())).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenReturn(request);

        RequestDto dto = requestService.cancelRequest(USER_ID, REQUEST_ID);

        assertThat(dto.getStatus()).isEqualTo(RequestStatus.CANCELED);
        verify(requestRepository, times(1)).findById(REQUEST_ID);
        verify(requestRepository, times(1)).save(request);
    }

    @Test
    void getRequests_returnsRequestsFromRequestService() {
        long requestId = 10L;
        RequestDto requestDto = createTestRequestDto(requestId, EVENT_ID, USER_ID, RequestStatus.PENDING);
        Request request = createTestRequest(requestId, EVENT_ID, USER_ID, RequestStatus.PENDING);
        List<RequestDto> expectedRequests = List.of(requestDto);
        List<Request> requests = List.of(request);

        doNothing().when(requestCheckService).checkEventExistence(anyLong(), anyLong());
        when(requestRepository.findByEventId(EVENT_ID)).thenReturn(requests);

        List<RequestDto> result = requestService.getRequests(USER_ID, EVENT_ID);

        assertThat(result).isEqualTo(expectedRequests);
        verify(requestCheckService).checkEventExistence(USER_ID, EVENT_ID);
        verify(requestRepository).findByEventId(EVENT_ID);
    }

    @Test
    void getRequests_throwsNotFoundException_whenEventDoesNotExist() {
        doThrow(new NotFoundException(Entities.USER, USER_ID))
                .when(requestCheckService)
                .checkEventExistence(USER_ID, EVENT_ID);
        assertThrows(NotFoundException.class, () -> requestService.getRequests(USER_ID, EVENT_ID));
        verify(requestCheckService, times(1)).checkEventExistence(USER_ID, EVENT_ID);
    }

    @Test
    void updateRequestStatuses_confirmsSomeAndRejectsOthers_whenLimitReached() {
        long requestId1 = 1L;
        long requestId2 = 2L;
        long requestId3 = 3L;
        List<Long> ids = List.of(requestId1, requestId2, requestId3);
        EventDto event = new EventDto(EVENT_ID, 2, USER_ID, 3, true, EventStatus.PUBLISHED);

        UpdateRequestStatusDto update = createTestUpdateRequestStatusDto(ids, RequestStatus.CONFIRMED);

        when(eventFetchClientAdapter.getEventByIdAndUserId(anyLong(), anyLong())).thenReturn(new RemoteCallResult.Success<>(event));

        Request confirmed = createTestRequest(requestId1, EVENT_ID, 1, RequestStatus.PENDING);;
        Request rejected1 = createTestRequest(requestId2, EVENT_ID, 2, RequestStatus.PENDING);
        Request rejected2 = createTestRequest(requestId3, EVENT_ID, 3, RequestStatus.PENDING);

        List<Long> idsToConfirm = List.of(requestId1);
        List<Long> idsToReject = List.of(requestId2, requestId3);
        List<Request> requestsToConfirm = List.of(confirmed);
        List<Request> requestsToReject = List.of(rejected1, rejected2);

        when(requestRepository.findByIdIn(idsToConfirm)).thenReturn(requestsToConfirm);
        when(requestRepository.findByIdIn(idsToReject)).thenReturn(requestsToReject);

        EventDto updatedEvent = new EventDto(EVENT_ID, 3, USER_ID, 3, true, EventStatus.PUBLISHED);
        when(eventFetchClientAdapter.updateConfirmedRequests(anyLong(), any(ConfirmedRequestsUpdate.class)))
                .thenReturn(new RemoteCallResult.Success<>(updatedEvent));

        ChangedRequestStatusesDto result = requestService.updateRequestStatuses(USER_ID, EVENT_ID, update);

        RequestDto expectedConfirmed = createTestRequestDto(requestId1, EVENT_ID, 1, RequestStatus.CONFIRMED);
        RequestDto expectedRejected1 = createTestRequestDto(requestId2, EVENT_ID, 2, RequestStatus.REJECTED);
        RequestDto expectedRejected2 = createTestRequestDto(requestId3, EVENT_ID, 3, RequestStatus.REJECTED);

        assertThat(result.getConfirmedRequests()).isEqualTo(List.of(expectedConfirmed));
        assertThat(result.getRejectedRequests()).isEqualTo(List.of(expectedRejected1, expectedRejected2));
        verify(eventFetchClientAdapter, times(1)).updateConfirmedRequests(EVENT_ID, new ConfirmedRequestsUpdate(3));
    }

    @Test
    void updateRequestStatuses_rejectsAll_whenNoSpotsAvailable() {
        long requestId1 = 1L;
        long requestId2 = 2L;
        long requestId3 = 3L;
        List<Long> ids = List.of(requestId1, requestId2, requestId3);
        EventDto event = new EventDto(EVENT_ID, 2, USER_ID, 2, true, EventStatus.PUBLISHED);

        UpdateRequestStatusDto update = createTestUpdateRequestStatusDto(ids, RequestStatus.CONFIRMED);

        when(eventFetchClientAdapter.getEventByIdAndUserId(anyLong(), anyLong())).thenReturn(new RemoteCallResult.Success<>(event));

        assertThrows(UnavailableUpdateException.class,
                () -> requestService.updateRequestStatuses(USER_ID, EVENT_ID, update));
    }

    @Test
    void updateRequestStatuses_allowsAll_whenNoLimitOrNoModeration() {
        long requestId1 = 1L;
        long requestId2 = 2L;
        long requestId3 = 3L;
        List<Long> ids = List.of(requestId1, requestId2, requestId3);
        EventDto event = new EventDto(EVENT_ID, 5, USER_ID, 0, false, EventStatus.PUBLISHED);

        UpdateRequestStatusDto update = createTestUpdateRequestStatusDto(ids, RequestStatus.CONFIRMED);

        Request confirmed1 = createTestRequest(requestId1, EVENT_ID, 1, RequestStatus.PENDING);;
        Request confirmed2 = createTestRequest(requestId2, EVENT_ID, 2, RequestStatus.PENDING);
        Request confirmed3 = createTestRequest(requestId3, EVENT_ID, 3, RequestStatus.PENDING);

        List<Request> requestsToConfirm = List.of(confirmed1, confirmed2, confirmed3);

        when(eventFetchClientAdapter.getEventByIdAndUserId(anyLong(), anyLong())).thenReturn(new RemoteCallResult.Success<>(event));
        when(requestRepository.findByIdIn(ids)).thenReturn(requestsToConfirm);

        RequestDto expectedConfirmed1 = createTestRequestDto(requestId1, EVENT_ID, 1, RequestStatus.CONFIRMED);;
        RequestDto expectedConfirmed2 = createTestRequestDto(requestId2, EVENT_ID, 2, RequestStatus.CONFIRMED);
        RequestDto expectedConfirmed3 = createTestRequestDto(requestId3, EVENT_ID, 3, RequestStatus.CONFIRMED);

        EventDto updatedEvent = new EventDto(EVENT_ID, 3, USER_ID, 3, true, EventStatus.PUBLISHED);
        when(eventFetchClientAdapter.updateConfirmedRequests(anyLong(), any(ConfirmedRequestsUpdate.class)))
                .thenReturn(new RemoteCallResult.Success<>(updatedEvent));

        ChangedRequestStatusesDto result = requestService.updateRequestStatuses(USER_ID, EVENT_ID, update);

        assertThat(result.getConfirmedRequests()).isEqualTo(List.of(expectedConfirmed1, expectedConfirmed2, expectedConfirmed3));
        assertThat(result.getRejectedRequests()).isEqualTo(List.of());

        verify(eventFetchClientAdapter, times(1)).updateConfirmedRequests(EVENT_ID, new ConfirmedRequestsUpdate(8));
    }

    private RequestDto createTestRequestDto(long requestId, long eventId, long requesterId, RequestStatus status) {
        return RequestDto.builder()
                .id(requestId)
                .event(eventId)
                .requester(requesterId)
                .created(LocalDateTime.of(2026, 1, 1, 10, 0, 0))
                .status(status)
                .build();
    }

    private Request createTestRequest(long requestId, long eventId, long requesterId, RequestStatus status) {
        return Request.builder()
                .id(requestId)
                .eventId(eventId)
                .requesterId(requesterId)
                .created(LocalDateTime.of(2026, 1, 1, 10, 0, 0))
                .status(status)
                .build();
    }

    private UpdateRequestStatusDto createTestUpdateRequestStatusDto(List<Long> requestIds, RequestStatus status) {
        return UpdateRequestStatusDto.builder()
                .requestIds(requestIds)
                .status(status)
                .build();
    }
}
