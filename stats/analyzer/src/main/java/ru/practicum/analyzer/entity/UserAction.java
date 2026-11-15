package ru.practicum.analyzer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_actions")
@IdClass(UserActionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAction {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "rating", nullable = false)
    private double rating;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
}
