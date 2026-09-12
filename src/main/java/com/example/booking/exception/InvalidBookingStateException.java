package com.example.booking.exception;

import com.example.booking.domain.BookingStatus;

/**
 * Например, попытка отменить уже COMPLETED бронь. Проверка конечного
 * автомата статусов живёт в BookingService.assertTransitionAllowed —
 * держать её в сервисе, а не в сеттере сущности, важно: сущность не должна
 * знать о бизнес-правилах, иначе тестировать переходы можно только
 * через полноценный Hibernate-контекст, а не unit-тестом на голом объекте.
 */
public class InvalidBookingStateException extends DomainException {

    public InvalidBookingStateException(BookingStatus current, BookingStatus target) {
        super("Cannot transition booking from %s to %s".formatted(current, target));
    }
}