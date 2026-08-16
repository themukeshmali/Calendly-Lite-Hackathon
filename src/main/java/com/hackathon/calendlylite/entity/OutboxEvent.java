package com.hackathon.calendlylite.entity;

import com.hackathon.calendlylite.enums.OutboxStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    // ===== Constructors =====
    public OutboxEvent() {}

    // ===== Getters / Setters =====
    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastAttemptedAt() { return lastAttemptedAt; }
    public void setLastAttemptedAt(LocalDateTime lastAttemptedAt) { this.lastAttemptedAt = lastAttemptedAt; }

    // ===== Builder =====
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final OutboxEvent e = new OutboxEvent();
        public Builder eventType(String t) { e.eventType = t; return this; }
        public Builder payload(String p) { e.payload = p; return this; }
        public Builder status(OutboxStatus s) { e.status = s; return this; }
        public Builder retryCount(int r) { e.retryCount = r; return this; }
        public OutboxEvent build() { return e; }
    }
}
