package com.example.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Отдельный конфиг-класс, а не аннотация прямо на Application — так проще
 * находить "точки включения" инфраструктурных фич глазами при код-ревью:
 * весь @Enable* живёт в одном пакете config, а не размазан по проекту.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}