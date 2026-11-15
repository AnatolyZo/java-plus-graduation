package ru.practicum.analyzer.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties({UserActionConsumerProperties.class, EventSimilarityConsumerProperties.class})
public class KafkaConfig {

    @Bean("userActionConsumer")
    public Consumer<Long, SpecificRecordBase> userActionConsumer(UserActionConsumerProperties properties) {
        return new KafkaConsumer<>(consumerConfig(properties));
    }

    @Bean("eventSimilarityConsumer")
    public Consumer<Long, SpecificRecordBase> eventSimilarityConsumer(EventSimilarityConsumerProperties properties) {
        return new KafkaConsumer<>(consumerConfig(properties));
    }

    public <T extends Props> Properties consumerConfig(T properties) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, properties.keyDeserializer());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, properties.valueDeserializer());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, properties.enableAutoCommit());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, properties.clientId());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.groupId());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.autoOffsetReset());
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.maxPollRecords());
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, properties.maxPollInterval());

        return config;
    }
}
