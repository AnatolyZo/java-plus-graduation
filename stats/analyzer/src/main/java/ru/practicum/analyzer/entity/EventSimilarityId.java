package ru.practicum.analyzer.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventSimilarityId {
    private Long eventA;
    private Long eventB;
}
