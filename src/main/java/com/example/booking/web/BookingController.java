package com.example.booking.web;

import com.example.booking.dto.BookingResponse;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Контроллер намеренно "тонкий": никакой бизнес-логики, только
 * HTTP <-> DTO маппинг и делегирование в сервис. Если здесь появляется
 * if с бизнес-смыслом — это сигнал, что он должен переехать в сервис.
 *
 * /api/v1/ — версионирование через URL-префикс. Это самый простой и
 * explicit подход (альтернативы: Accept-header versioning, отдельный
 * заголовок X-API-Version) — стоит знать плюсы/минусы обоих на собеседовании:
 * URL-версионирование проще кэшировать и логировать, header-versioning
 * "чище" по REST-канонам, но менее прозрачен в логах nginx/gateway.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse response = bookingService.createBooking(userId, request, idempotencyKey);
        // 201 Created + Location header, указывающий на созданный ресурс —
        // это то, что действительно отличает "REST по учебнику" от
        // "HTTP-эндпоинт, который что-то возвращает". Проверяют часто.
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + response.id())).body(response);
    }

    /**
     * POST, а не PUT/PATCH — это переход состояния (action), а не замена
     * ресурса. PUT семантически подразумевает "заменить ресурс целиком
     * присланным представлением", что не соответствует смыслу "подтвердить".
     *
     * TODO(security-iteration): здесь нет проверки, что confirm/cancel
     * вызывает владелец брони или админ — сейчас любой валидный X-User-Id
     * может подтвердить чужую бронь. Авторизацию (например,
     * @PreAuthorize("@bookingSecurity.isOwner(#bookingId, principal)"))
     * добавим вместе со Spring Security. Фиксирую это здесь осознанно,
     * а не молчу — на собеседовании такая пометка показывает, что ты видишь
     * дыру в текущем состоянии кода, а не считаешь его законченным.
     */
    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.confirmBooking(bookingId));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId));
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> getMyBookings(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(bookingService.getUserBookings(userId));
    }
}