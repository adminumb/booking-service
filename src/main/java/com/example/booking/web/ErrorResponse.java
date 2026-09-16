package com.example.booking.web;

import java.time.Instant;

/**
 * Единый формат ошибки для всего API. Без этого разные исключения
 * возвращались бы клиенту в разной форме, и фронтенду/интегратору
 * пришлось бы парсить каждую ошибку по-своему — обычно это одна
 * из первых вещей, которые проверяют на код-ревью в коммерческом проекте.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}