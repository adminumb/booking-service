package com.example.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Общий родитель для всех сущностей.
 *
 * Почему MappedSuperclass, а не @Entity + наследование таблиц:
 * MappedSuperclass не создаёт отдельную таблицу и не участвует в иерархии
 * запросов Hibernate — его поля просто "вливаются" в таблицу наследника.
 * Для полей аудита (id, createdAt, updatedAt) это ровно то, что нужно:
 * они не самостоятельная сущность, а инфраструктурная часть каждой сущности.
 *
 * UUID вместо auto-increment Long:
 * - не палим количество броней конкурентам через инкрементальный id;
 * - легко генерировать id на стороне клиента/сервиса ДО вставки в БД
 *   (полезно для идемпотентности запросов, см. BookingService);
 * - нет проблем при шардинге/слиянии данных из нескольких инстансов.
 * Минус — index на UUID тяжелее, чем на Long, но для pet-проекта это
 * осознанный компромисс, который стоит уметь объяснить на собеседовании.
 */
@Getter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(of = "id")
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
