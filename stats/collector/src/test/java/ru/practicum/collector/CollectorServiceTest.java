package ru.practicum.collector;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.collector.service.CollectorService;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.grpc.stats.service.collector.ActionTypeProto;
import ru.practicum.grpc.stats.service.collector.UserActionProto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectorServiceTest {

    private static final String TOPIC = "stats.user-actions.v1";

    @Mock
    private Producer<Long, SpecificRecordBase> producer;

    @Mock
    private StreamObserver<Empty> responseObserver;

    private CollectorService service;

    @BeforeEach
    void setUp() {
        service = new CollectorService(producer);
        ReflectionTestUtils.setField(service, "userActionsTopic", TOPIC);
    }

    private UserActionProto buildRequest(ActionTypeProto type) {
        return UserActionProto.newBuilder()
                .setUserId(10L)
                .setEventId(20L)
                .setActionType(type)
                .setTimestamp(Timestamp.newBuilder().setSeconds(100).setNanos(500).build())
                .build();
    }

    @Test
    void shouldSendCorrectRecordAndCompleteCall() {
        service.collectUserAction(buildRequest(ActionTypeProto.ACTION_VIEW), responseObserver);

        ArgumentCaptor<ProducerRecord<Long, SpecificRecordBase>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture());

        ProducerRecord<Long, SpecificRecordBase> record = captor.getValue();
        assertEquals(TOPIC, record.topic());
        assertEquals(10L, record.key());

        UserActionAvro value = (UserActionAvro) record.value();
        assertEquals(10L, value.getUserId());
        assertEquals(20L, value.getEventId());
        assertEquals(ActionTypeAvro.VIEW, value.getActionType());
        assertEquals(Instant.ofEpochSecond(100, 500).truncatedTo(ChronoUnit.MILLIS),
                value.getTimestamp());

        verify(responseObserver).onNext(Empty.getDefaultInstance());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }

    @Test
    void shouldReturnInternalErrorForUnknownActionType() {
        UserActionProto request = UserActionProto.newBuilder()
                .setUserId(10L)
                .setEventId(20L)
                .setActionTypeValue(999)
                .setTimestamp(Timestamp.newBuilder().setSeconds(100).build())
                .build();

        assertEquals(ActionTypeProto.UNRECOGNIZED, request.getActionType());

        service.collectUserAction(request, responseObserver);

        ArgumentCaptor<StatusRuntimeException> errorCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(errorCaptor.capture());

        StatusRuntimeException error = errorCaptor.getValue();
        assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
        assertTrue(error.getMessage().contains("неизвестный тип действия"));

        verify(producer, never()).send(any(ProducerRecord.class));
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void shouldReturnInternalErrorWhenProducerFails() {
        doThrow(new RuntimeException("kafka is down"))
                .when(producer).send(any(ProducerRecord.class));

        service.collectUserAction(buildRequest(ActionTypeProto.ACTION_LIKE), responseObserver);

        ArgumentCaptor<StatusRuntimeException> errorCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(errorCaptor.capture());

        StatusRuntimeException error = errorCaptor.getValue();
        assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
        assertTrue(error.getMessage().contains("kafka is down"));

        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void shouldCloseProducerOnDestroy() {
        service.destroy();
        verify(producer).close();
    }
}