package ru.practicum.analyzer.config;

public interface Props {
    String bootstrapServers();

    String keyDeserializer();

    String valueDeserializer();

    String enableAutoCommit();

    String clientId();

    String groupId();

    String autoOffsetReset();

    String maxPollRecords();

    String maxPollInterval();
}
