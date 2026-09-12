package com.example.booking.exception;

/**
 * Бросается в двух разных ситуациях, которые стоит различать на собеседовании:
 *
 * 1) Бизнес-конфликт: слот уже занят другой активной бронью
 *    (обнаружено заранее через findOverlapping — "мягкая" проверка).
 * 2) Гоночный конфликт: два запроса прошли шаг 1 одновременно и оба
 *    попытались вставить/обновить одну и ту же запись — обнаруживается
 *    постфактум через уникальный constraint в БД или OptimisticLockException.
 *
 * И то и другое клиенту стоит вернуть как HTTP 409 Conflict — с точки зрения
 * API это один и тот же класс ошибок, разница важна только внутри сервиса.
 */
public class BookingConflictException extends DomainException {

    public BookingConflictException(String message) {
        super(message);
    }
}