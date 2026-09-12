package com.example.booking.domain;

/**
 * Тип бронируемого ресурса. Один и тот же движок бронирования
 * (проверка пересечений по времени, оптимистичная блокировка, события)
 * обслуживает разные предметные области — это то самое architecture reuse,
 * которое любят видеть на собеседовании вместо трёх копипаст-сервисов.
 */
public enum ResourceType {
    MEETING_ROOM,
    WORKSPACE_SEAT,
    EVENT_TICKET
}
