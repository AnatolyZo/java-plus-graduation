package ru.practicum.collector.service;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import ru.practicum.collector.mapper.EnumMapper;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.grpc.stats.service.collector.ActionTypeProto;
import ru.practicum.grpc.stats.service.collector.UserActionControllerGrpc;
import ru.practicum.grpc.stats.service.collector.UserActionProto;

import java.time.Instant;
import java.util.Optional;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class CollectorService extends UserActionControllerGrpc.UserActionControllerImplBase {
    private final Producer<Long, SpecificRecordBase> producer;

    @Value("${kafka.topics.user-actions}")
    private String userActionsTopic;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        try {
            SpecificRecordBase action = toAvro(request);
            ProducerRecord<Long, SpecificRecordBase> record = new ProducerRecord<>(userActionsTopic, request.getUserId(), action);
            producer.send(record);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL
                            .withDescription(e.getLocalizedMessage())
                            .withCause(e)
            ));
        }
    }

    @PreDestroy
    public void destroy() {
        producer.close();
        log.debug("Продюсер закрыт");
    }

    private SpecificRecordBase toAvro(UserActionProto request) {
        return UserActionAvro.newBuilder()
                .setUserId(request.getUserId())
                .setEventId(request.getEventId())
                .setActionType(toActionTypeAvro(request.getActionType()))
                .setTimestamp(toAvroTimestamp(request.getTimestamp()))
                .build();
    }

    private ActionTypeAvro toActionTypeAvro(ActionTypeProto proto) {
        Optional<ActionTypeAvro> actionType = EnumMapper.toAppEnum(ActionTypeAvro.values(), proto.name());

        if (actionType.isPresent()) {
            return actionType.get();
        } else {
            throw new IllegalArgumentException("Передан неизвестный тип действия пользователя " + proto.name());
        }
    }

    private Instant toAvroTimestamp(com.google.protobuf.Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
