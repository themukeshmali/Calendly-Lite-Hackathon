package com.hackathon.calendlylite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Calendly-Lite — Simplified Booking System
 *
 * Patterns implemented:
 *  1. Idempotency         — Idempotency-Key header prevents duplicate bookings
 *  2. Distributed Lock    — Redis lock prevents concurrent booking of same slot
 *  3. Outbox Pattern      — Transactional outbox ensures no lost notifications
 *  4. Retry Logic         — Failed outbox events are retried up to 3 times
 *  5. Dead Letter Queue   — Exhausted retries move to dead_letter_events table
 *  6. Caching             — Available slots cached in Redis (5 min TTL)
 *  7. Rate Limiting       — Bucket4j limits booking attempts per IP
 */
@SpringBootApplication
@EnableCaching        // Activates Redis caching (@Cacheable, @CacheEvict)
@EnableScheduling     // Activates the Outbox Worker (@Scheduled)
public class CalendlyLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendlyLiteApplication.class, args);
    }
}
