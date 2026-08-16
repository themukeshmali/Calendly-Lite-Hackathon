package com.hackathon.calendlylite.controller;

import com.hackathon.calendlylite.config.RateLimiterConfig;
import com.hackathon.calendlylite.dto.ApiResponse;
import com.hackathon.calendlylite.dto.BookingRequest;
import com.hackathon.calendlylite.entity.Booking;
import com.hackathon.calendlylite.service.BookingService;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Bookings", description = "Book, view, and cancel time slots")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;
    private final RateLimiterConfig rateLimiterConfig;

    public BookingController(BookingService bookingService, RateLimiterConfig rateLimiterConfig) {
        this.bookingService = bookingService;
        this.rateLimiterConfig = rateLimiterConfig;
    }

    @PostMapping("/api/slots/{id}/book")
    @Operation(
        summary = "Book a time slot",
        description = "Required header: `Idempotency-Key: <UUID>`. Rate limited to 5 requests/min per IP."
    )
    public ResponseEntity<ApiResponse<Booking>> bookSlot(
            @PathVariable Long id,
            @Parameter(description = "Unique UUID per booking attempt", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest request,
            HttpServletRequest httpRequest) {

        // ── Rate Limiting ────────────────────────────────────────────────────
        String clientIp = resolveClientIp(httpRequest);
        Bucket bucket = rateLimiterConfig.resolveBucketForIp(clientIp);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Rate limit exceeded: max 5 booking attempts per minute."));
        }

        // ── Idempotency-Key validation ───────────────────────────────────────
        // required=false above so Spring does not throw before we reach this check,
        // which lets us return a structured ApiResponse instead of a raw Spring error.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Idempotency-Key header is required."));
        }

        Booking booking = bookingService.bookSlot(id, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Slot booked", booking));
    }

    @GetMapping("/api/bookings/{id}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<ApiResponse<Booking>> getBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Booking found", bookingService.getBooking(id)));
    }

    @DeleteMapping("/api/bookings/{id}")
    @Operation(summary = "Cancel a booking — frees the slot back to OPEN")
    public ResponseEntity<ApiResponse<Booking>> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Booking cancelled", bookingService.cancelBooking(id)));
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
