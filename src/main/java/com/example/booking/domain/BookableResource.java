package com.example.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Ресурс, который можно забронировать: переговорка, рабочее место, билет
 * на событие. Пересечения по времени и блокировки проверяются не здесь,
 * а в BookingService — сущность сама по себе "глупая", хранит только
 * инвариантные для ресурса данные (вместимость, тип, активность).
 */
@Entity
@Table(name = "bookable_resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BookableResource extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceType type;

    @Column(nullable = false)
    private Integer capacity;

    @Column(length = 255)
    private String location;

    /**
     * Мягкое отключение ресурса (на ремонте, снят с продажи) вместо
     * физического DELETE — на ресурс уже могут ссылаться существующие брони,
     * а исторические данные (кто и что бронировал) терять нельзя.
     */
    @Column(nullable = false)
    private boolean active;
}
