package com.printscheduler.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Exposes a shared, configured {@link ObjectMapper} bean.
 *
 * <p>Explicitly declared so WebSocket components can inject it via constructor
 * injection regardless of auto-config ordering.
 *
 * <p>Compatible with Spring Boot 4 / Jackson 3.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // Jackson 3 auto-registers Java Time module by default; no explicit register needed.
        return mapper;
    }
}
