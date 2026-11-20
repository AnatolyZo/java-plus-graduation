package ru.practicum.aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.consumer.aggregator")
public record ConsumerProperties(
        String bootstrapServers,
        String keyDeserializer,
        String valueDeserializer,
        String enableAutoCommit,
        String clientId,
        String groupId,
        String autoOffsetReset,
        String maxPollRecords,
        String maxPollInterval
) {
}
