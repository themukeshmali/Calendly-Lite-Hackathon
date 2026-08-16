package com.hackathon.calendlylite.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_event_id", nullable = false)
    private Long originalEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "moved_at", nullable = false, updatable = false)
    private LocalDateTime movedAt;

    // ===== Constructors =====
    public DeadLetterEvent() {}

    // ===== Getters =====
    public Long getId() { return id; }
    public Long getOriginalEventId() { return originalEventId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getMovedAt() { return movedAt; }

    // ===== Builder =====
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final DeadLetterEvent e = new DeadLetterEvent();
        public Builder originalEventId(Long id) { e.originalEventId = id; return this; }
        public Builder eventType(String t) { e.eventType = t; return this; }
        public Builder payload(String p) { e.payload = p; return this; }
        public Builder failureReason(String r) { e.failureReason = r; return this; }
        public DeadLetterEvent build() { return e; }
    }
}
