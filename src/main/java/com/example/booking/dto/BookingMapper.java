package com.example.booking.dto;

import com.example.booking.domain.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct генерирует реализацию маппера во время компиляции (annotation
 * processing) — это plain Java-код без рефлексии, в отличие от ModelMapper
 * или ручного маппинга через рефлексию. Разница ощутима на нагрузке:
 * MapStruct — это по сути обычные геттеры/сеттеры, которые ты бы написал
 * руками, только сгенерированные автоматически и проверяемые компилятором
 * (опечатка в имени поля — ошибка компиляции, а не runtime NPE).
 *
 * componentModel = "spring" — сгенерированная реализация становится
 * Spring-бином и инжектится через конструктор как обычная зависимость.
 */
@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "resourceId", source = "resource.id")
    @Mapping(target = "resourceName", source = "resource.name")
    @Mapping(target = "userId", source = "user.id")
    BookingResponse toResponse(Booking booking);
}