package com.example.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * record, а не класс с Lombok — с Java 16+ record это ровно то, что нужно
 * для неизменяемого DTO: конструктор, геттеры, equals/hashCode/toString
 * генерируются компилятором, без единой строчки boilerplate и без Lombok.
 * Хороший маркер "человек следит за современным Java", это любят спрашивать
 * отдельно ("что нового ты используешь из последних версий Java").
 */
public record CreateBookingRequest(

        @NotNull(message = "resourceId must not be null")
        UUID resourceId,

        @NotNull(message = "startTime must not be null")
        @Future(message = "startTime must be in the future")
        Instant startTime,

        @NotNull(message = "endTime must not be null")
        @Future(message = "endTime must be in the future")
        Instant endTime
) {
    /**
     * Компактный конструктор record — место для инвариантной валидации,
     * которую декларативными аннотациями не выразить (сравнение двух полей
     * между собой). @NotNull/@Future проверит Bean Validation на границе
     * контроллера, а вот "start < end" — уже бизнес-инвариант самого DTO.
     */
    public CreateBookingRequest {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }
}