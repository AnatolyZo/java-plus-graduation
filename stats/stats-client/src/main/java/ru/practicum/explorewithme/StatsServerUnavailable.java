package ru.practicum.explorewithme;

public class StatsServerUnavailable extends RuntimeException {
    public StatsServerUnavailable(String message, Exception e) {
        super(message, e);
    }
}
