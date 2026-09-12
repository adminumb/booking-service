package com.example.booking.dto;

import com.example.booking.domain.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID resourceId,
        String resourceName,
        UUID userId,
        Instant startTime,
        Instant endTime,
        BookingStatus status
) {
}