package com.hackathon.calendlylite.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI configuration.
 * After starting the app, open: http://localhost:8080/swagger-ui.html
 * This gives you a beautiful interactive API explorer.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI calendlyLiteOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Calendly-Lite API")
                        .description("""
                                Simplified booking/scheduling system built for the Google Hackathon 2026.
                                
                                **Patterns Implemented:**
                                - 🔑 **Idempotency** — Duplicate booking requests are safely handled via Idempotency-Key header
                                - 🔒 **Distributed Lock** — Redis-based locking prevents concurrent double-bookings
                                - 📬 **Outbox Pattern** — Transactional outbox ensures no lost notifications
                                - 🔁 **Retry Logic** — Failed events retried up to 3 times with backoff
                                - 💀 **Dead Letter Queue** — Exhausted retries parked in dead_letter_events
                                - ⚡ **Caching** — Available slots cached in Redis (5 min TTL)
                                - 🚦 **Rate Limiting** — Max 5 booking attempts/minute per IP
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Mukesh Mali")
                                .email("mukesh@hackathon.dev")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local Development")));
    }
}
