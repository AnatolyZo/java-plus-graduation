package ru.practicum.event.dto.event;

import jakarta.validation.constraints.PositiveOrZero;

public record ConfirmedRequestsUpdate(@PositiveOrZero int confirmedRequests) {
}
