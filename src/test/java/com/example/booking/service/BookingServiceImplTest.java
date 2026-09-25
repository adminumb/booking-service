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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookableResourceRepository resourceRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private BookingMapper bookingMapper;

    private BookingServiceImpl service;

    private UUID resourceId;
    private UUID userId;
    private BookableResource resource;
    private AppUser user;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(bookingRepository, resourceRepository, userRepository, bookingMapper);

        resourceId = UUID.randomUUID();
        userId = UUID.randomUUID();

        resource = BookableResource.builder().id(resourceId).name("Room 42").capacity(4).active(true).build();
        user = AppUser.builder().email("dev@example.com").build();

        Instant start = Instant.now().plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofHours(1));
        request = new CreateBookingRequest(resourceId, start, end);
    }

    @Test
    void createBooking_savesAndReturnsMappedResponse_whenSlotIsFree() {
        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bookingRepository.findOverlapping(eq(resourceId), any(), any())).thenReturn(List.of());

        Booking savedBooking = Booking.builder().status(BookingStatus.PENDING).build();
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(savedBooking);

        BookingResponse expectedResponse = new BookingResponse(
                UUID.randomUUID(), resourceId, "Room 42", userId,
                request.startTime(), request.endTime(), BookingStatus.PENDING);
        when(bookingMapper.toResponse(savedBooking)).thenReturn(expectedResponse);

        BookingResponse result = service.createBooking(userId, request, "idem-key-1");

        assertThat(result).isEqualTo(expectedResponse);
        verify(bookingRepository).saveAndFlush(any(Booking.class));
    }

    @Test
    void createBooking_throwsConflict_whenOverlappingBookingExists() {
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Booking existingOverlap = Booking.builder().status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findOverlapping(eq(resourceId), any(), any()))
                .thenReturn(List.of(existingOverlap));

        assertThatThrownBy(() -> service.createBooking(userId, request, null))
                .isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_returnsExistingBooking_whenIdempotencyKeyAlreadyUsed() {
        Booking existing = Booking.builder().status(BookingStatus.PENDING).build();
        when(bookingRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existing));

        BookingResponse expected = new BookingResponse(
                UUID.randomUUID(), resourceId, "Room 42", userId,
                request.startTime(), request.endTime(), BookingStatus.PENDING);
        when(bookingMapper.toResponse(existing)).thenReturn(expected);

        BookingResponse result = service.createBooking(userId, request, "dup-key");

        assertThat(result).isEqualTo(expected);
        // Идемпотентный повтор не должен трогать resource/user репозитории —
        // мы вообще не должны доходить до бизнес-проверок повторно.
        verify(resourceRepository, never()).findById(any());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_throwsNotFound_whenResourceDoesNotExist() {
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(userId, request, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmBooking_throwsInvalidState_whenBookingAlreadyCancelled() {
        UUID bookingId = UUID.randomUUID();
        Booking cancelled = Booking.builder().status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.confirmBooking(bookingId))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmBooking_transitionsPendingToConfirmed() {
        UUID bookingId = UUID.randomUUID();
        Booking pending = Booking.builder().status(BookingStatus.PENDING).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(pending));
        when(bookingRepository.saveAndFlush(pending)).thenReturn(pending);

        BookingResponse expected = new BookingResponse(
                bookingId, resourceId, "Room 42", userId,
                request.startTime(), request.endTime(), BookingStatus.CONFIRMED);
        when(bookingMapper.toResponse(pending)).thenReturn(expected);

        BookingResponse result = service.confirmBooking(bookingId);

        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
