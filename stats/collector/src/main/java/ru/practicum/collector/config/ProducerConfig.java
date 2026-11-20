package ru.practicum.collector.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(ProducerProperties.class)
public class ProducerConfig {

    @Bean
    public Producer<Long, SpecificRecordBase> kafkaProducer(ProducerProperties properties) {
        Properties config = new Properties();
        config.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, properties.keySerializer());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, properties.valueSerializer());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.CLIENT_ID_CONFIG, properties.clientId());

        return new KafkaProducer<>(config);
    }
}
