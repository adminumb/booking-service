package com.example.booking.web;

import com.example.booking.domain.ResourceType;
import com.example.booking.dto.ResourceResponse;
import com.example.booking.service.BookableResourceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final BookableResourceQueryService resourceQueryService;

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> listAvailable(@RequestParam ResourceType type) {
        // Spring сам конвертирует query-параметр в enum (ResourceType) через
        // встроенный ConversionService — если придёт значение вне enum,
        // получим 400 через MethodArgumentTypeMismatchException автоматически,
        // без единой строчки ручного парсинга.
        return ResponseEntity.ok(resourceQueryService.findAvailableByType(type));
    }
}