package ru.practicum.request;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.practicum.request.controller.PrivateRequestController;
import ru.practicum.request.dto.ChangedRequestStatusesDto;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.request.dto.RequestStatus;
import ru.practicum.request.dto.UpdateRequestStatusDto;
import ru.practicum.request.service.RequestService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static ru.practicum.common.ControllerConstants.ACCESS_PRIVATE;

@ExtendWith(MockitoExtension.class)
public class PrivateRequestControllerTests {

    private static final String URL_BASE = ACCESS_PRIVATE;
    private static final long TEST_USER_ID = 1L;
    private static final long TEST_EVENT_ID = 10L;
    private static final long TEST_REQUEST_ID = 5L;

    @Mock
    private RequestService service;

    @InjectMocks
    private PrivateRequestController controller;

    private RequestDto createRequestDto(long id, long eventId, long requesterId, RequestStatus status) {
        return RequestDto.builder()
                .id(id)
                .created(LocalDateTime.of(2026, 1, 1, 12, 0))
                .event(eventId)
                .requester(requesterId)
                .status(status)
                .build();
    }

    @Test
    void getRequests_returnsResponseEntityWithOkAndList() {
        long userId = TEST_USER_ID;
        long eventId = TEST_EVENT_ID;

        RequestDto req1 = createRequestDto(1L, eventId, userId, RequestStatus.PENDING);
        RequestDto req2 = createRequestDto(2L, eventId, userId + 1, RequestStatus.CONFIRMED);
        List<RequestDto> expectedBody = List.of(req1, req2);

        when(service.getRequests(eq(userId), eq(eventId))).thenReturn(expectedBody);

        ResponseEntity<List<RequestDto>> result = controller.getRequests(userId, eventId);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody()).hasSize(2);
        Assertions.assertThat(result.getBody().get(0).getId()).isEqualTo(req1.getId());
        Assertions.assertThat(result.getBody().get(1).getStatus()).isEqualTo(RequestStatus.CONFIRMED);
    }

    @Test
    void updateRequestStatuses_returnsResponseEntityWithOkAndDto() {
        long userId = TEST_USER_ID;
        long eventId = TEST_EVENT_ID;

        UpdateRequestStatusDto body = UpdateRequestStatusDto.builder()
                .requestIds(List.of(1L, 2L))
                .status(RequestStatus.CONFIRMED)
                .build();

        RequestDto confirmed1 = createRequestDto(1L, eventId, userId, RequestStatus.CONFIRMED);
        RequestDto confirmed2 = createRequestDto(2L, eventId, userId, RequestStatus.CONFIRMED);
        RequestDto rejected1 = createRequestDto(3L, eventId, userId, RequestStatus.REJECTED);

        ChangedRequestStatusesDto expectedBody = ChangedRequestStatusesDto.builder()
                .confirmedRequests(List.of(confirmed1, confirmed2))
                .rejectedRequests(List.of(rejected1))
                .build();

        when(service.updateRequestStatuses(eq(userId), eq(eventId), any(UpdateRequestStatusDto.class)))
                .thenReturn(expectedBody);

        ResponseEntity<ChangedRequestStatusesDto> result = controller.updateRequestStatuses(userId, eventId, body);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getConfirmedRequests()).hasSize(2);
        Assertions.assertThat(result.getBody().getRejectedRequests()).hasSize(1);
        Assertions.assertThat(result.getBody().getConfirmedRequests().get(0).getStatus())
                .isEqualTo(RequestStatus.CONFIRMED);
    }

    @Test
    void addRequest_returnsResponseEntityWithCreatedAndDto() {
        long userId = TEST_USER_ID;
        long eventId = TEST_EVENT_ID;

        RequestDto created = createRequestDto(99L, eventId, userId, RequestStatus.PENDING);

        when(service.create(eq(userId), eq(eventId))).thenReturn(created);

        ResponseEntity<RequestDto> result = controller.addRequest(userId, eventId);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(created.getId());
        Assertions.assertThat(result.getBody().getStatus()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void getUserRequests_returnsListDirectly_noResponseEntity() {
        // Важно: этот метод в контроллере НЕ возвращает ResponseEntity, а сразу List<RequestDto>
        long userId = TEST_USER_ID;

        RequestDto req1 = createRequestDto(10L, 5L, userId, RequestStatus.PENDING);
        RequestDto req2 = createRequestDto(11L, 7L, userId, RequestStatus.CANCELED);
        List<RequestDto> expected = List.of(req1, req2);

        when(service.getUserRequests(eq(userId))).thenReturn(expected);

        List<RequestDto> actual = controller.getUserRequests(userId);

        Assertions.assertThat(actual).isNotNull();
        Assertions.assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void cancelRequest_returnsResponseEntityWithOkAndDto() {
        long userId = TEST_USER_ID;
        long requestId = TEST_REQUEST_ID;

        RequestDto canceled = RequestDto.builder()
                .id(TEST_REQUEST_ID)
                .status(RequestStatus.CANCELED)
                .build();

        when(service.cancelRequest(eq(userId), eq(requestId))).thenReturn(canceled);

        ResponseEntity<RequestDto> result = controller.cancelRequest(userId, requestId);

        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getBody()).isNotNull();
        Assertions.assertThat(result.getBody().getId()).isEqualTo(requestId);
        Assertions.assertThat(result.getBody().getStatus()).isEqualTo(RequestStatus.CANCELED);
    }
}