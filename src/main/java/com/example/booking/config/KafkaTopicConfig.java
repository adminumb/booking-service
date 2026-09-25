package com.example.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Явное создание топиков кодом (NewTopic-бины) вместо auto-create на брокере
 * (мы его выключили в docker-compose: KAFKA_AUTO_CREATE_TOPICS_ENABLE=false).
 * Partitions и replication-factor — часть архитектурного решения, а не
 * случайность первого запроса, который создал топик по умолчанию с 1
 * партицией и её потом пришлось бы менять с даунтаймом.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String BOOKING_EVENTS_TOPIC = "booking-events";
    public static final String BOOKING_EVENTS_DLT = "booking-events.DLT";

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(BOOKING_EVENTS_TOPIC)
                // 3 партиции — компромисс для pet-проекта между параллелизмом
                // консьюмеров (partitions = верхняя граница параллельных
                // консьюмеров в одной consumer group) и накладными расходами.
                // В проде число считают от реальной нагрузки и количества
                // инстансов consumer-сервиса, а не берут "круглое число".
                .partitions(3)
                .replicas(1) // для single-broker локального окружения;
                // в проде replication-factor обычно 3 — переживает потерю
                // одного брокера без потери данных топика.
                .build();
    }

    @Bean
    public NewTopic bookingEventsDeadLetterTopic() {
        // Отдельный DLT (dead-letter topic) — сообщения, которые консьюмер
        // не смог обработать после всех ретраев (например, постоянно падает
        // на конкретном payload — "poison message"), уезжают сюда вместо
        // того чтобы блокировать партицию бесконечными ретраями одного и
        // того же битого сообщения. См. KafkaConsumerConfig.errorHandler.
        return TopicBuilder.name(BOOKING_EVENTS_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}