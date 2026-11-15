package ru.practicum.aggregator.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties({ProducerProperties.class, ConsumerProperties.class})
public class KafkaConfig {

    @Bean
    public Producer<Long, SpecificRecordBase> kafkaProducer(ProducerProperties properties) {
        Properties config = new Properties();
        config.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, properties.keySerializer());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, properties.valueSerializer());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.CLIENT_ID_CONFIG, properties.clientId());

        return new KafkaProducer<>(config);
    }

    @Bean
    public Consumer<Long, SpecificRecordBase> kafkaConsumer(ConsumerProperties properties) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, properties.keyDeserializer());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, properties.valueDeserializer());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, properties.enableAutoCommit());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, properties.clientId());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.clientId());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.autoOffsetReset());
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.maxPollRecords());
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, properties.maxPollInterval());

        return new KafkaConsumer<>(config);
    }
}
