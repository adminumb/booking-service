package com.example.booking.exception;

/**
 * Базовое доменное исключение — не наследуем каждое от RuntimeException
 * напрямую, чтобы в GlobalExceptionHandler (итерация 3) можно было ловить
 * все доменные ошибки одним catch-блоком, если понадобится, и чтобы
 * у всех была единая точка расширения (например, общий errorCode).
 */
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}