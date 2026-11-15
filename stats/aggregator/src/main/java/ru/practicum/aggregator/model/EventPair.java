package ru.practicum.aggregator.model;

public record EventPair(long eventA, long eventB) {
    public static EventPair of(long a, long b) {
        return new EventPair(Math.min(a, b), Math.max(a, b));
    }

    public long getOppositeIdTo(long eventId) {
        return eventA == eventId ? eventB : eventA;
    }
}
