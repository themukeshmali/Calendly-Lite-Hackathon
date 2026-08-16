package com.hackathon.calendlylite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson configuration.
 *
 * Registers JavaTimeModule so LocalDateTime is serialized correctly
 * and disables WRITE_DATES_AS_TIMESTAMPS so dates come out as ISO-8601 strings.
 *
 * This single shared instance is injected wherever ObjectMapper is needed
 * (e.g. BookingService.buildPayload) instead of creating a new one per call.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
