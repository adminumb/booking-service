package com.example.booking.domain;

/**
 * Конечный автомат состояний брони:
 *
 *   PENDING --confirm--> CONFIRMED --complete--> COMPLETED
 *      |                     |
 *      +------cancel---------+---------------> CANCELLED
 *
 * Переходы валидируются в BookingService (не в контроллере и не в БД) —
 * это обычно отдельный вопрос: "где у тебя живёт бизнес-логика".
 * Ответ: в доменном/сервисном слое, контроллер — тонкий, только маппинг
 * HTTP <-> DTO и вызов сервиса.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
