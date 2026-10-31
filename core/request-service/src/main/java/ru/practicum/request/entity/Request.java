package ru.practicum.request.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.practicum.request.dto.RequestStatus;

import java.time.LocalDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "requests", schema = "public")
public class Request {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "created")
    private LocalDateTime created;

    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "requester_id", nullable = false)
    private long requesterId;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;
}
