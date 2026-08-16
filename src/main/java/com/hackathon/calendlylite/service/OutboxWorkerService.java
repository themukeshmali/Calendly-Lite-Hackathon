package com.hackathon.calendlylite.service;

import com.hackathon.calendlylite.entity.DeadLetterEvent;
import com.hackathon.calendlylite.entity.OutboxEvent;
import com.hackathon.calendlylite.enums.OutboxStatus;
import com.hackathon.calendlylite.repository.DeadLetterEventRepository;
import com.hackathon.calendlylite.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Outbox Worker — polls the outbox_events table every 5 seconds.
 *
 * Implements:
 *  - Retry Logic: failed events are retried up to MAX_RETRIES times
 *  - Dead Letter Queue: events exceeding MAX_RETRIES are moved to dead_letter_events
 *
 * NOTE: A 30% simulated failure rate is active for demo purposes.
 * Remove the simulation block before deploying to production.
 */
@Service
public class OutboxWorkerService {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorkerService.class);

    private static final int    MAX_RETRIES            = 3;
    private static final double SIMULATED_FAILURE_RATE = 0.30; // remove in production

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;

    public OutboxWorkerService(OutboxEventRepository outboxEventRepository,
                               DeadLetterEventRepository deadLetterEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("Outbox worker: processing {} PENDING event(s)", pending.size());
        pending.forEach(this::processEvent);
    }

    private void processEvent(OutboxEvent event) {
        event.setLastAttemptedAt(LocalDateTime.now());

        try {
            // ── Simulated failure (30% rate) — remove in production ──────────
            if (ThreadLocalRandom.current().nextDouble() < SIMULATED_FAILURE_RATE) {
                throw new RuntimeException("Simulated notification failure (demo only)");
            }

            // ── Success: mark as sent ────────────────────────────────────────
            log.info("📧 Sending [{}]: {}", event.getEventType(), event.getPayload());
            event.setStatus(OutboxStatus.SENT);
            outboxEventRepository.save(event);
            log.info("✅ Event SENT: id={} type={}", event.getId(), event.getEventType());

        } catch (Exception ex) {

            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            log.warn("❌ Event FAILED: id={} attempt={}/{} reason={}", event.getId(), retries, MAX_RETRIES, ex.getMessage());

            if (retries >= MAX_RETRIES) {
                // ── Dead Letter Queue: exhausted all retries ─────────────────
                deadLetterEventRepository.save(DeadLetterEvent.builder()
                        .originalEventId(event.getId())
                        .eventType(event.getEventType())
                        .payload(event.getPayload())
                        .failureReason(ex.getMessage())
                        .build());

                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                log.error("💀 Event id={} moved to Dead Letter Queue after {} failures", event.getId(), MAX_RETRIES);
            } else {
                // ── Keep PENDING — will be retried on next scheduler cycle ───
                outboxEventRepository.save(event);
                log.info("🔁 Event id={} will retry ({}/{})", event.getId(), retries, MAX_RETRIES);
            }
        }
    }
}
