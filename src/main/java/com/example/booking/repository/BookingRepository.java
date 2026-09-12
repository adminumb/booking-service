package com.example.booking.repository;

import com.example.booking.domain.Booking;
import com.example.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    /**
     * Проверка пересечения интервалов: два интервала [s1,e1) и [s2,e2)
     * пересекаются тогда и только тогда, когда s1 < e2 AND s2 < e1.
     * Это классический interval-overlap алгоритм — его почти гарантированно
     * попросят вывести на доске на собеседовании, полезно проговорить вслух.
     *
     * Отменённые брони (CANCELLED) не считаются занятыми — статус исключаем.
     *
     * pessimistic write lock не ставим здесь намеренно: этот select используется
     * как pre-check перед INSERT, а финальную защиту от гонки даёт связка
     * "уникальный индекс в БД + обработка DataIntegrityViolationException"
     * (создадим индекс и разберём это в итерации 2 — сервисный слой).
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.resource.id = :resourceId
              AND b.status <> com.example.booking.domain.BookingStatus.CANCELLED
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    List<Booking> findOverlapping(
            @Param("resourceId") UUID resourceId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    /**
     * JOIN FETCH явно подгружает resource и user одним запросом (одним JOIN),
     * вместо N+1 отдельных SELECT по каждой ленивой ссылке при обходе списка.
     * Это ответ на вопрос "как ты решаешь проблему N+1 в реальном коде",
     * альтернатива — @EntityGraph, обсудим на практике при подключении API.
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.resource
            JOIN FETCH b.user
            WHERE b.user.id = :userId
            ORDER BY b.startTime DESC
            """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Booking> findAllByUserIdWithDetails(@Param("userId") UUID userId);

    List<Booking> findAllByStatus(BookingStatus status);
}
