package com.example.booking.dto;

import com.example.booking.domain.ResourceType;

import java.util.UUID;

public record ResourceResponse(
        UUID id,
        String name,
        ResourceType type,
        Integer capacity,
        String location
) {
}