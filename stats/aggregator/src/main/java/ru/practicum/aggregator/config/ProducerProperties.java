package ru.practicum.aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.producer")
public record ProducerProperties(
        String bootstrapServers,
        String keySerializer,
        String valueSerializer,
        String clientId
) {
}