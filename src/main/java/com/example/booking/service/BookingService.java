package com.example.booking.service;

import com.example.booking.dto.BookingResponse;
import com.example.booking.dto.CreateBookingRequest;

import java.util.List;
import java.util.UUID;

/**
 * Интерфейс отдельно от реализации — сознательный выбор ради Dependency
 * Inversion: контроллер зависит от абстракции, а не от конкретного класса.
 * Практическая польза, которую стоит проговорить: в тестах контроллера
 * (@WebMvcTest) мокаем этот интерфейс через @MockBean без поднятия
 * реальной бизнес-логики. Честный контраргумент, который тоже стоит знать:
 * если реализация всего одна и не планируется вторая — некоторые команды
 * сознательно не заводят интерфейс, чтобы не плодить лишний слой абстракции
 * (аргумент "YAGNI"). Здесь завожу интерфейс намеренно — это пет-проект
 * для демонстрации паттерна, обсудим оба взгляда на собеседовании.
 */
public interface BookingService {

    BookingResponse createBooking(UUID userId, CreateBookingRequest request, String idempotencyKey);

    BookingResponse confirmBooking(UUID bookingId);

    BookingResponse cancelBooking(UUID bookingId);

    List<BookingResponse> getUserBookings(UUID userId);
}