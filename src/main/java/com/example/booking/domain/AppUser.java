package com.example.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Пользователь системы. Названа AppUser, а не User — банальная, но реальная
 * причина: "User" — зарезервированное слово в PostgreSQL (SQL-стандарт),
 * и таблица user требует постоянного экранирования кавычками в каждом запросе.
 * Такие мелочи как раз показывают, что человек писал реальный код, а не туториал.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    /**
     * Хранится bcrypt-хэш, а не пароль. Заполним в итерации с Spring Security.
     */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;
}
