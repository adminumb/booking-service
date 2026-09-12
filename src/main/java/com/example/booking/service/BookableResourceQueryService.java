package com.example.booking.service;

import com.example.booking.domain.ResourceType;
import com.example.booking.dto.ResourceResponse;
import com.example.booking.repository.BookableResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookableResourceQueryService {

    private final BookableResourceRepository resourceRepository;

    public List<ResourceResponse> findAvailableByType(ResourceType type) {
        return resourceRepository.findAllByTypeAndActiveTrue(type).stream()
                .map(r -> new ResourceResponse(r.getId(), r.getName(), r.getType(), r.getCapacity(), r.getLocation()))
                .toList();
    }
}