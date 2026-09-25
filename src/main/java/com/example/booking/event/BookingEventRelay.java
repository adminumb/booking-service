package com.example.booking.event;

import com.example.booking.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Решает "dual write" проблему в её самой частой и самой недооценённой
 * форме: если публиковать в Kafka прямо внутри бизнес-метода сервиса
 * (до commit), а транзакция БД потом по какой-то причине откатится
 * (например, из-за гонки на @Version в параллельном confirmBooking) —
 * в Kafka уже улетело событие о том, чего по факту не произошло.
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) решает эту половину
 * проблемы: слушатель вызывается ТОЛЬКО если транзакция, в которой было
 * вызвано applicationEventPublisher.publishEvent(...), успешно закоммитилась.
 * Если транзакция падает — событие в Kafka просто никогда не уйдёт, и это
 * ровно то поведение, которое нужно.
 *
 * ЧТО ЭТО НЕ РЕШАЕТ (снова стоит проговорить честно, как и с overlap-гонкой
 * в BookingServiceImpl): транзакция БД уже закоммичена, а сам процесс
 * может упасть ДО того, как этот listener успеет отправить сообщение
 * в Kafka (краш JVM, обрыв сети до брокера) — событие будет потеряно
 * без следа, потому что оно нигде не сохранено, кроме памяти. Полное
 * решение — Transactional Outbox: событие пишется в отдельную таблицу
 * outbox_events В ТОЙ ЖЕ транзакции, что и основные данные (значит,
 * либо закоммитятся оба, либо ни одного), а отдельный поллер или
 * Debezium/CDC-коннектор асинхронно вычитывает эту таблицу и шлёт в Kafka
 * гарантированно, с at-least-once. Не стал вводить Outbox с первой же
 * Kafka-итерации, чтобы не тащить сразу три новых сущности — но это
 * прямой ответ на вопрос "как сделать доставку событий надёжной по-настоящему".
 */
@Component
@RequiredArgsConstructor
public class BookingEventRelay {

    private static final Logger log = LoggerFactory.getLogger(BookingEventRelay.class);
    private static final String TOPIC = KafkaTopicConfig.BOOKING_EVENTS_TOPIC;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEvent(BookingEvent event) {
        // Partition key = bookingId.toString() — см. обоснование в
        // BookingEventType: все события одной брони идут в одну партицию,
        // сохраняя порядок между собой.
        kafkaTemplate.send(TOPIC, event.bookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Именно здесь, а не выше, проявляется тот самый
                        // остаточный риск потери события, описанный в javadoc
                        // класса — на этом месте в прод-системе с Outbox
                        // стояла бы отметка "событие отправлено" в БД,
                        // а без неё поллер отправил бы его снова при ретрае.
                        log.error("Failed to publish {} for booking={}",
                                event.eventType(), event.bookingId(), ex);
                    } else {
                        log.info("Published {} for booking={} to partition={}, offset={}",
                                event.eventType(), event.bookingId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}