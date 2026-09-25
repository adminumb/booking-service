package com.example.booking.config;

import com.example.booking.event.BookingEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Почему это не оставлено на дефолтную spring.kafka.* автоконфигурацию
 * из application.yml: JsonSerializer/JsonDeserializer по умолчанию создают
 * СОБСТВЕННЫЙ internal ObjectMapper, который ничего не знает про
 * java.time.Instant без JavaTimeModule — Instant в BookingEvent улетел бы
 * в Kafka как массив [seconds, nanos] вместо читаемой ISO-строки, и любой
 * не-Java консьюмер (условный Python-сервис аналитики) сломался бы на
 * парсинге. Регистрируем ObjectMapper явно — Spring Boot настраивает
 * JavaTimeModule автоматически только для Jackson HttpMessageConverter
 * (REST-ответы), но не для Kafka-сериализаторов: это два независимых
 * места конфигурации, и это стоит знать и явно проговорить.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // acks=all + enable.idempotence=true — Kafka-протокольная
        // идемпотентность продюсера. Стоит чётко отличать от нашей
        // application-level идемпотентности на стороне консьюмера
        // (см. NotificationEventListener + ProcessedEvent) — это две
        // РАЗНЫЕ гарантии на разных уровнях стека:
        //   - producer idempotence защищает от дублей, которые создал бы
        //     САМ клиент при retry отправки на нестабильной сети (брокер
        //     получил и записал, ack потерялся по дороге назад, клиент
        //     считает что не отправилось и шлёт снова — без idempotence
        //     в топике оказалась бы физическая дублирующая запись;
        //     с idempotence брокер дедуплицирует по sequence number
        //     для пары producer_id+partition);
        //   - ProcessedEvent защищает от дублей уже на уровне БИЗНЕС-
        //     обработки сообщения консьюмером — например, при rebalance
        //     партиций консьюмер может перечитать уже обработанный offset.
        // Один механизм не заменяет другой, нужны оба слоя одновременно.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);
        factory.setValueSerializer(new JsonSerializer<>(kafkaObjectMapper));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DefaultKafkaConsumerFactory<String, BookingEvent> consumerFactory(
            ObjectMapper kafkaObjectMapper) {

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                keyAwareValueDeserializer(kafkaObjectMapper)
        );
    }

    /**
     * ErrorHandlingDeserializer оборачивает JsonDeserializer: если
     * десериализация payload'а падает (битый JSON, несовместимая схема
     * после деплоя новой версии продюсера), ошибка не роняет весь poll-loop
     * консьюмера (что без обёртки "подвесило" бы консьюмер на этом
     * сообщении навсегда), а передаётся в container error handler
     * (см. KafkaListenerContainerConfig), который отправит именно это
     * сообщение прямиком в DLT — десериализацию повторной попыткой
     * всё равно не исправить, ретраить её бессмысленно.
     */
    private ErrorHandlingDeserializer<BookingEvent> keyAwareValueDeserializer(
            ObjectMapper kafkaObjectMapper) {

        JsonDeserializer<BookingEvent> jsonDeserializer =
                new JsonDeserializer<>(BookingEvent.class, kafkaObjectMapper, false);

        jsonDeserializer.addTrustedPackages("com.example.booking.event");

        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }
}