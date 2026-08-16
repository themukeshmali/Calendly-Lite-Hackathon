package com.hackathon.calendlylite.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RATE LIMITER — Bucket4j based, per-IP
 *
 * Uses the Token Bucket algorithm:
 *  - Each IP gets a "bucket" of 5 tokens
 *  - Each booking request costs 1 token
 *  - Tokens refill at 5 per minute
 *  - When bucket is empty → HTTP 429 Too Many Requests
 *
 * This prevents bots/abusers from spamming the booking endpoint.
 */
@Component
public class RateLimiterConfig {

    // One bucket per IP address — stored in memory (ConcurrentHashMap for thread safety)
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int CAPACITY = 5;                        // 5 requests
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1); // per minute

    /**
     * Get or create a bucket for a given IP address.
     * Called on every booking request.
     */
    public Bucket resolveBucketForIp(String ipAddress) {
        return buckets.computeIfAbsent(ipAddress, this::createNewBucket);
    }

    private Bucket createNewBucket(String ip) {
        // Simple fixed window: 5 requests per minute
        Bandwidth limit = Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(CAPACITY, REFILL_PERIOD)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
