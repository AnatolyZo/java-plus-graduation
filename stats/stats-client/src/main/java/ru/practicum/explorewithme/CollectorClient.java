package ru.practicum.explorewithme;

import com.google.protobuf.Timestamp;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.grpc.stats.service.collector.ActionTypeProto;
import ru.practicum.grpc.stats.service.collector.UserActionControllerGrpc;
import ru.practicum.grpc.stats.service.collector.UserActionProto;

import java.time.Instant;

@Component
public class CollectorClient {
    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void addView(long userId, long eventId) {
        UserActionProto request = buildRequest(userId, eventId, ActionTypeProto.ACTION_VIEW);
        client.collectUserAction(request);
    }

    public void addRegistration(long userId, long eventId) {
        UserActionProto request = buildRequest(userId, eventId, ActionTypeProto.ACTION_REGISTER);
        client.collectUserAction(request);
    }

    public void addLike(long userId, long eventId) {
        UserActionProto request = buildRequest(userId, eventId, ActionTypeProto.ACTION_LIKE);
        client.collectUserAction(request);
    }

    private UserActionProto buildRequest(long userId, long eventId, ActionTypeProto type) {
        return UserActionProto.newBuilder()
                .setUserId(userId)
                .setEventId(eventId)
                .setActionType(type)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .setNanos(Instant.now().getNano())
                        .build())
                .build();
    }
}
