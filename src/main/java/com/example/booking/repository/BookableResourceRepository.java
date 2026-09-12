package com.example.booking.repository;

import com.example.booking.domain.BookableResource;
import com.example.booking.domain.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookableResourceRepository extends JpaRepository<BookableResource, UUID> {

    List<BookableResource> findAllByTypeAndActiveTrue(ResourceType type);
}
