package com.hackathon.calendlylite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.calendlylite.dto.BookingRequest;
import com.hackathon.calendlylite.entity.AvailabilitySlot;
import com.hackathon.calendlylite.entity.Booking;
import com.hackathon.calendlylite.entity.OutboxEvent;
import com.hackathon.calendlylite.enums.BookingStatus;
import com.hackathon.calendlylite.enums.OutboxStatus;
import com.hackathon.calendlylite.enums.SlotStatus;
import com.hackathon.calendlylite.exception.ApiException;
import com.hackathon.calendlylite.repository.AvailabilitySlotRepository;
import com.hackathon.calendlylite.repository.BookingRepository;
import com.hackathon.calendlylite.repository.OutboxEventRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final int MAX_LOCK_WAIT_SECONDS = 3;
    private static final int LOCK_LEASE_SECONDS    = 10;

    private final BookingRepository bookingRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public BookingService(BookingRepository bookingRepository,
                          AvailabilitySlotRepository slotRepository,
                          OutboxEventRepository outboxEventRepository,
                          RedissonClient redissonClient,
                          CacheManager cacheManager,
                          ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.redissonClient = redissonClient;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Books a slot atomically.
     *
     * Patterns applied (in order):
     *  1. Idempotency  — same Idempotency-Key returns the existing booking
     *  2. Distributed Lock — per-slot Redis lock prevents concurrent double-bookings
     *  3. Business check   — slot must still be OPEN under the lock
     *  4. Outbox           — booking + notification event committed in one transaction
     *  5. Cache eviction   — slot list cache cleared so next read is fresh
     */
    @Transactional
    public Booking bookSlot(Long slotId, String idempotencyKey, BookingRequest request) {

        // ── 1. IDEMPOTENCY ──────────────────────────────────────────────────
        Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency hit key={} — returning existing booking id={}", idempotencyKey, existing.get().getId());
            return existing.get();
        }

        // ── 2. DISTRIBUTED LOCK ─────────────────────────────────────────────
        RLock lock = redissonClient.getLock("lock:slot:" + slotId);
        boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(MAX_LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!lockAcquired) {
                throw new ApiException(
                        "Slot " + slotId + " is currently being booked. Try again in a moment.",
                        HttpStatus.CONFLICT);
            }
            log.debug("Lock acquired on slot={}", slotId);

            // ── 3. BUSINESS CHECK ────────────────────────────────────────────
            AvailabilitySlot slot = slotRepository.findById(slotId)
                    .orElseThrow(() -> new ApiException("Slot not found: " + slotId, HttpStatus.NOT_FOUND));

            if (slot.getStatus() != SlotStatus.OPEN) {
                throw new ApiException(
                        "Slot " + slotId + " is not available (status: " + slot.getStatus() + ")",
                        HttpStatus.CONFLICT);
            }

            // ── 4. SAVE BOOKING + OUTBOX (same transaction) ──────────────────
            slot.setStatus(SlotStatus.BOOKED);
            slotRepository.save(slot);

            Booking booking = Booking.builder()
                    .slot(slot)
                    .guestName(request.getGuestName())
                    .guestEmail(request.getGuestEmail())
                    .idempotencyKey(idempotencyKey)
                    .status(BookingStatus.CONFIRMED)
                    .build();

            Booking saved = bookingRepository.save(booking);
            log.info("Booking created: id={} guest={} slot={}", saved.getId(), request.getGuestEmail(), slotId);

            outboxEventRepository.save(OutboxEvent.builder()
                    .eventType("BOOKING_CONFIRMED")
                    .payload(buildPayload(saved))
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build());
            log.debug("Outbox event queued for booking id={}", saved.getId());

            // ── 5. CACHE EVICTION ────────────────────────────────────────────
            // NOTE: We call CacheManager directly (not @CacheEvict) because @CacheEvict
            // uses Spring AOP — self-invocation within the same bean bypasses the proxy
            // and the eviction would be silently skipped.
            evictSlotsCache(slot.getHost().getId());

            return saved;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released on slot={}", slotId);
            }
        }
    }

    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException("Booking not found: " + bookingId, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = getBooking(bookingId);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException("Booking " + bookingId + " is already cancelled", HttpStatus.CONFLICT);
        }

        AvailabilitySlot slot = booking.getSlot();
        slot.setStatus(SlotStatus.OPEN);
        slotRepository.save(slot);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);

        evictSlotsCache(slot.getHost().getId());

        outboxEventRepository.save(OutboxEvent.builder()
                .eventType("BOOKING_CANCELLED")
                .payload(buildPayload(saved))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());

        log.info("Booking cancelled: id={}", bookingId);
        return saved;
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private void evictSlotsCache(Long hostId) {
        Objects.requireNonNull(cacheManager.getCache("slots")).evict(hostId);
        log.debug("Cache evicted for host={}", hostId);
    }

    private String buildPayload(Booking booking) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bookingId",  booking.getId(),
                    "guestName",  booking.getGuestName(),
                    "guestEmail", booking.getGuestEmail(),
                    "slotId",     booking.getSlot().getId(),
                    "slotStart",  booking.getSlot().getStartTime().toString(),
                    "slotEnd",    booking.getSlot().getEndTime().toString(),
                    "status",     booking.getStatus().name()
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize booking payload for id={}", booking.getId(), e);
            return "{\"bookingId\": " + booking.getId() + "}";
        }
    }
}
