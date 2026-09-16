package com.example.booking.web;

import com.example.booking.exception.BookingConflictException;
import com.example.booking.exception.InvalidBookingStateException;
import com.example.booking.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * @RestControllerAdvice — единая точка перевода доменных/валидационных
 * исключений в HTTP-ответы. Контроллеры остаются "тонкими": никакого
 * try/catch внутри них, весь маппинг исключение -> статус-код живёт здесь.
 * Порядок хендлеров важен: более специфичные исключения — выше,
 * catch-all (Exception) — всегда последним, иначе он перехватит всё
 * раньше, чем Spring дойдёт до специфичных обработчиков (Spring выбирает
 * по точности совпадения типа, но явный порядок в файле облегчает чтение).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler({BookingConflictException.class, InvalidBookingStateException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest req) {
        // 409 Conflict — верный статус и для пересечения интервалов,
        // и для недопустимого перехода статуса: в обоих случаях запрос
        // синтаксически корректен, но конфликтует с текущим состоянием ресурса.
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        // Собираем все поля с ошибками в одно читаемое сообщение, а не отдаём
        // клиенту только первую найденную ошибку валидации — иначе он будет
        // чинить форму по одной ошибке за реквест, что раздражает интеграторов.
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> "%s: %s".formatted(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        // Сюда, в частности, прилетает инвариант "startTime must be before
        // endTime" из компактного конструктора CreateBookingRequest.
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        // Например, ?type=NOT_A_REAL_ENUM в ResourceController.listAvailable —
        // без этого хендлера ошибка конвертации типа падает в catch-all Exception
        // и клиент получает 500 вместо честного 400 за свой же некорректный ввод.
        String message = "Invalid value '%s' for parameter '%s'".formatted(ex.getValue(), ex.getName());
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        // Отсутствие обязательного X-User-Id/Idempotency-Key — тоже ошибка
        // клиента (400), а не сбой сервера. Общее правило, которое стоит
        // проговорить: 4xx/5xx у нас разделены по принципу "кто виноват" —
        // 4xx на всё, что клиент может исправить сам, 5xx — на то, что нет.
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Catch-all: логируем полный стектрейс себе (нужен для дебага),
        // а клиенту отдаём общее сообщение — не палим внутреннее устройство
        // системы (имена классов, SQL, пути) в теле ответа наружу.
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}