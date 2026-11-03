package ru.practicum.request.feign;

public record EventDto(
        long id,
        int confirmedRequests,
        long initiator,
        int participantLimit,
        boolean requestModeration,
        EventStatus state
) {
}
