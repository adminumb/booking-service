package com.example.booking.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Один и тот же record используется в двух ролях:
 * 1) как Spring ApplicationEvent (публикуется через ApplicationEventPublisher
 *    внутри @Transactional метода сервиса — см. BookingServiceImpl);
 * 2) как payload, который в итоге сериализуется в Kafka (см. BookingEventRelay).
 *
 * eventId генерируется на месте создания события, а не Kafka-фреймворком —
 * он и есть ключ идемпотентности для консьюмера (см. NotificationEventListener
 * и ProcessedEvent): при повторной доставке (at-least-once — Kafka по
 * умолчанию не гарантирует exactly-once на стороне консьюмера) eventId
 * будет тем же самым, и дубликат безопасно отбрасывается.
 */
public record BookingEvent(
        UUID eventId,
        BookingEventType eventType,
        UUID bookingId,
        UUID resourceId,
        UUID userId,
        Instant occurredAt
) {
    public static BookingEvent of(BookingEventType type, UUID bookingId, UUID resourceId, UUID userId) {
        return new BookingEvent(UUID.randomUUID(), type, bookingId, resourceId, userId, Instant.now());
    }
}