package ru.practicum.analyzer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "event_similarity")
@IdClass(EventSimilarityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSimilarity {
    @Id
    @Column(name = "event_a")
    private Long eventA;

    @Id
    @Column(name = "event_b")
    private Long eventB;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
}
