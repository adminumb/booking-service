package com.example.booking.service;

import com.example.booking.domain.AppUser;
import com.example.booking.domain.BookableResource;
import com.example.booking.domain.Booking;
import com.example.booking.domain.BookingStatus;
import com.example.booking.dto.BookingMapper;
import com.example.booking.dto.BookingResponse;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.exception.BookingConflictException;
import com.example.booking.exception.InvalidBookingStateException;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.AppUserRepository;
import com.example.booking.repository.BookableResourceRepository;
import com.example.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ЧЕСТНО О ГРАНИЦАХ ТЕКУЩЕЙ ЗАЩИТЫ ОТ ГОНОК (хороший пункт для обсуждения
 * на собеседовании — показывает, что ты видишь границы решения, а не
 * считаешь его серебряной пулей):
 *
 * Уникальный constraint на idempotency_key защищает от дублей ОДНОГО И ТОГО
 * ЖЕ запроса (ретрай клиента). Но НЕ защищает от двух РАЗНЫХ пользователей,
 * без idempotency-key, одновременно бронирующих пересекающиеся интервалы
 * одного ресурса — оба пройдут findOverlapping() до того, как второй увидит
 * запись первого, и оба успешно вставятся (это не конфликтует ни с одним
 * constraint-ом, потому что записи физически разные строки).
 *
 * Как это закрывается по-настоящему в проде на PostgreSQL: EXCLUDE
 * CONSTRAINT USING gist с range-типом по (resource_id, tsrange(start,end))
 * и расширением btree_gist — база физически не даст вставить пересекающуюся
 * бронь, независимо от того, что решил Java-код. Не стал заводить это в
 * Liquibase сразу, чтобы не усложнять итерацию 1 — но это ровно тот ответ,
 * который отличает middle от middle+: "я знаю, что текущая защита неполная,
 * и знаю, каким конкретно инструментом её закрыть".
 */
@Service
@RequiredArgsConstructor // конструкторная инъекция вместо @Autowired на полях —
// поля final, объект невозможно создать в невалидном состоянии, и тест
// может собрать сервис через `new` без поднятия Spring-контекста вообще.
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BookingRepository bookingRepository;
    private final BookableResourceRepository resourceRepository;
    private final AppUserRepository userRepository;
    private final BookingMapper bookingMapper;

    /**
     * Полный жизненный путь запроса на создание брони:
     *
     *  1. Идемпотентность: если клиент уже присылал этот idempotencyKey —
     *     не создаём вторую бронь, а отдаём результат первой попытки.
     *     Это защищает от дублей при сетевых ретраях со стороны клиента
     *     (запрос ушёл, ответ потерялся, клиент/gateway ретраит).
     *  2. Загружаем resource и user — оба обязаны существовать.
     *  3. "Мягкая" проверка пересечений (findOverlapping) — быстрый отсев
     *     подавляющего большинства конфликтов без похода к constraint-у БД.
     *  4. saveAndFlush вместо save — форсирует INSERT и проверку unique
     *     constraint СРАЗУ, внутри этого try/catch. Если оставить save(),
     *     Hibernate отложит физический INSERT до конца транзакции (flush
     *     на commit), и DataIntegrityViolationException прилетит уже ПОСЛЕ
     *     выхода из метода, на уровне транзакционного прокси — этот catch
     *     её просто не увидит. Мелкая деталь, которая ломает всю защиту,
     *     если её не знать — отличный вопрос "на подумать" для собеседования.
     *  5. Если constraint всё же словили (значит, кто-то с тем же
     *     idempotencyKey вставился на долю секунды раньше) — не считаем
     *     это ошибкой клиента, а отдаём его же результат: это и есть
     *     идемпотентность в действии, а не просто "не даём дублировать".
     */
    @Override
    @Transactional
    public BookingResponse createBooking(UUID userId, CreateBookingRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key={}, returning existing booking={}",
                        idempotencyKey, existing.get().getId());
                return bookingMapper.toResponse(existing.get());
            }
        }

        BookableResource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("BookableResource", request.resourceId()));

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", userId));

        List<Booking> overlapping = bookingRepository.findOverlapping(
                resource.getId(), request.startTime(), request.endTime());

        if (!overlapping.isEmpty()) {
            throw new BookingConflictException(
                    "Resource %s is already booked for %s - %s"
                            .formatted(resource.getId(), request.startTime(), request.endTime()));
        }

        Booking booking = Booking.builder()
                .resource(resource)
                .user(user)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(BookingStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            return bookingMapper.toResponse(saved);
        } catch (DataIntegrityViolationException raceLostOnUniqueConstraint) {
            log.warn("Lost idempotency race for key={}, fetching winner", idempotencyKey);
            return bookingRepository.findByIdempotencyKey(idempotencyKey)
                    .map(bookingMapper::toResponse)
                    .orElseThrow(() -> raceLostOnUniqueConstraint);
        }
    }

    /**
     * @Retryable на OptimisticLockingFailureException: если между нашим SELECT
     * и UPDATE кто-то другой успел изменить эту же бронь (например, отменил),
     * Hibernate бросит исключение о несовпадении version. Вместо того чтобы
     * сразу отдавать 409 клиенту, даём сервису ещё 2 попытки перечитать
     * актуальное состояние и попробовать снова — конфликты на уровне одной
     * строки обычно живут миллисекунды, retry их почти всегда гасит.
     * backoff с delay растит паузу между попытками (100ms, 200ms) —
     * exponential backoff, чтобы не долбить БД в busy-loop.
     */
    @Override
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public BookingResponse confirmBooking(UUID bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        assertTransitionAllowed(booking.getStatus(), BookingStatus.CONFIRMED);
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingMapper.toResponse(bookingRepository.saveAndFlush(booking));
    }

    @Override
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        assertTransitionAllowed(booking.getStatus(), BookingStatus.CANCELLED);
        booking.setStatus(BookingStatus.CANCELLED);
        return bookingMapper.toResponse(bookingRepository.saveAndFlush(booking));
    }

    @Override
    @Transactional(readOnly = true) // readOnly=true: Hibernate не заводит
    // dirty-checking snapshot для сущностей этой транзакции — меньше памяти
    // и накладных расходов на чтение, плюс явный сигнал коллегам "здесь
    // только читаем". Ещё один частый вопрос: "что даёт readOnly на практике".
    public List<BookingResponse> getUserBookings(UUID userId) {
        return bookingRepository.findAllByUserIdWithDetails(userId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    private Booking getBookingOrThrow(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    /**
     * Конечный автомат переходов статуса. switch-выражение (Java 14+,
     * стабильно с 17) вместо цепочки if/else — компилятор проверяет
     * исчерпываемость по всем константам enum: забудешь ветку — не
     * скомпилируется. Логика переходов живёт в сервисе, а не в сеттере
     * Booking — сущность не должна знать о бизнес-правилах (см. комментарий
     * в InvalidBookingStateException).
     */
    private void assertTransitionAllowed(BookingStatus current, BookingStatus target) {
        boolean allowed = switch (current) {
            case PENDING -> target == BookingStatus.CONFIRMED || target == BookingStatus.CANCELLED;
            case CONFIRMED -> target == BookingStatus.CANCELLED || target == BookingStatus.COMPLETED;
            case CANCELLED, COMPLETED -> false;
        };
        if (!allowed) {
            throw new InvalidBookingStateException(current, target);
        }
    }
}