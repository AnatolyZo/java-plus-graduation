package ru.practicum.analyzer.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.analyzer.entity.EventSimilarity;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventSimilarityProcessor implements Runnable {
    @Qualifier("eventSimilarityConsumer")
    private final Consumer<Long, SpecificRecordBase> consumer;
    private final EventSimilarityRepository repository;
    private static final Duration CONSUME_ATTEMPT_TIMEOUT = Duration.ofMillis(5000);

    @Value("${kafka.topics.event-similarity}")
    private String eventSimilarityTopic;

    @Override
    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

        try {
            consumer.subscribe(List.of(eventSimilarityTopic));

            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(CONSUME_ATTEMPT_TIMEOUT);

                Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();

                for (ConsumerRecord<Long, SpecificRecordBase> incomingRecord : records) {
                    try {
                        SpecificRecordBase base = incomingRecord.value();

                        if (base == null) {
                            log.warn("Переданное сообщение пусто");
                            putOffset(incomingRecord, offsets);
                            continue;
                        }

                        if (!(base instanceof EventSimilarityAvro eventSimilarityAvro)) {
                            log.warn("Получен неизвестный тип записи {}", base.getClass());
                            putOffset(incomingRecord, offsets);
                            continue;
                        }

                        if (eventSimilarityAvro.getEventA() <= 0 || eventSimilarityAvro.getEventB() <= 0 || eventSimilarityAvro.getScore() <= 0) {
                            log.warn("Получен объект с нулевыми/пустыми полями {}", base.getClass());
                            putOffset(incomingRecord, offsets);
                            continue;
                        }

                        EventSimilarity eventSimilarity = EventSimilarity.builder()
                                .eventA(eventSimilarityAvro.getEventA())
                                .eventB(eventSimilarityAvro.getEventB())
                                .score(eventSimilarityAvro.getScore())
                                .timestamp(eventSimilarityAvro.getTimestamp())
                                .build();

                        repository.upsertSimilarity(
                                eventSimilarity.getEventA(),
                                eventSimilarity.getEventB(),
                                eventSimilarity.getScore(),
                                eventSimilarity.getTimestamp()
                        );

                        putOffset(incomingRecord, offsets);
                    } catch (Exception e) {
                        log.error("Ошибка обработки снапшота, offset = {}, key = {}", incomingRecord.offset(), incomingRecord.key(), e);
                        break;
                    }
                }

                if (!offsets.isEmpty()) {
                    consumer.commitSync(offsets);
                }
            }
        } catch (WakeupException e) {
            log.debug("Поток пробуждён после прерывания ожидания; продолжаем выполнение.", e);
        } finally {
            consumer.close();
        }
    }

    private void putOffset(ConsumerRecord<Long, SpecificRecordBase> incomingRecord, Map<TopicPartition, OffsetAndMetadata> offsets) {
        offsets.put(
                new TopicPartition(incomingRecord.topic(), incomingRecord.partition()),
                new OffsetAndMetadata(incomingRecord.offset() + 1)
        );
    }
}
