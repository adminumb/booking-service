package com.example.booking.exception;

import java.util.UUID;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String entityName, UUID id) {
        super("%s not found: id=%s".formatted(entityName, id));
    }
}