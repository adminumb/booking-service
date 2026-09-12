package com.example.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Бронь конкретного ресурса на временной интервал.
 *
 * Ключевые архитектурные решения, которые нужно уметь объяснить:
 *
 * 1) FetchType.LAZY на обеих связях.
 *    Дефолт для @ManyToOne в JPA — EAGER, и это классическая "грабля":
 *    выборка списка броней тянет за собой resource и user на каждую строку,
 *    даже если они не нужны (типичный источник N+1). LAZY — осознанный выбор,
 *    подгружаем связанные сущности явно через JOIN FETCH там, где они правда нужны.
 *
 * 2) @Version (оптимистичная блокировка).
 *    Сценарий гонки: два запроса одновременно читают один и тот же ресурс,
 *    видят слот свободным и оба пытаются создать бронь. Без блокировки —
 *    двойное бронирование. С @Version Hibernate добавляет `WHERE version = ?`
 *    в UPDATE; кто вторым не совпал по version — получает OptimisticLockException.
 *    Почему не пессимистичная (SELECT ... FOR UPDATE)? Конфликты по конкретному
 *    слоту редки относительно общего потока запросов — оптимистичная блокировка
 *    не держит лок в БД на время бизнес-логики и лучше масштабируется.
 *    Проверку самого пересечения интервалов (overlap check) мы всё равно
 *    делаем отдельным запросом в сервисе — @Version защищает только от гонки
 *    "два апдейта одной и той же строки", а не от двух разных INSERT.
 *    Про это отдельно поговорим в итерации с сервисным слоем.
 *
 * 3) EnumType.STRING для статуса и ORDINAL-ловушка уже объяснены в BookingStatus.
 *
 * 4) Индекс на (resource_id, start_time, end_time) — он нужен, потому что
 *    основной "горячий" запрос сервиса — это поиск пересекающихся броней
 *    по ресурсу и диапазону времени (см. BookingRepository, итерация 2).
 */
@Entity
@Table(
        name = "bookings",
        indexes = @Index(name = "idx_booking_resource_time", columnList = "resource_id,start_time,end_time")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private BookableResource resource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    /**
     * Ключ идемпотентности, который клиент присылает в заголовке запроса.
     * Нужен, чтобы повторный сетевой ретрай (клиент не получил ответ,
     * но бронь уже создалась) не породил вторую бронь. Уникальный индекс
     * на это поле — обсудим и создадим в Liquibase-миграции ниже.
     */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Version
    private Long version;
}
