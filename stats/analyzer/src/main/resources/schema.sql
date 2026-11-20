CREATE TABLE user_actions (
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    rating DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,

    PRIMARY KEY (user_id, event_id)
);

CREATE TABLE event_similarity (
    event_a BIGINT NOT NULL,
    event_b BIGINT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,

    PRIMARY KEY (event_a, event_b)
);