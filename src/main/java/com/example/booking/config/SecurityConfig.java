package com.example.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ВРЕМЕННАЯ конфигурация. spring-boot-starter-security уже в classpath
 * (понадобится для JWT позже), а значит Spring Boot автоконфигурация
 * по умолчанию закрыла бы вообще ВСЕ эндпоинты Basic Auth со случайно
 * сгенерированным паролем в логах при каждом старте — без явного
 * SecurityFilterChain API было бы невозможно вызвать ни через Postman,
 * ни из тестов. Пока сознательно открываем всё, чтобы не блокировать
 * работу над API-слоем; в итерации с JWT этот бин будет полностью заменён
 * на реальную проверку токена + authorizeHttpRequests с ролями.
 *
 * sessionCreationPolicy(STATELESS) уже выставляем сейчас — это архитектурное
 * решение не про безопасность как таковую, а про то, что REST API не должен
 * плодить HttpSession на сервере: сервис должен горизонтально масштабироваться
 * без sticky-сессий, а аутентификация (когда появится) будет только через
 * токен в каждом запросе, а не через cookie+сессию.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // CSRF актуален для браузерных
                // форм с cookie-сессиями; для stateless REST API с токенами
                // в заголовке он не защищает ни от чего и только мешает —
                // это тоже стандартный вопрос на собеседовании ("а почему
                // у вас csrf выключен, это же дыра?").
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}