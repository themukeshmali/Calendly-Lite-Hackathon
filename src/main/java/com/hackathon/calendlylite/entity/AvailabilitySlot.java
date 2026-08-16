package com.hackathon.calendlylite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hackathon.calendlylite.enums.SlotStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "availability_slots")
public class AvailabilitySlot implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status = SlotStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ===== Constructors =====
    public AvailabilitySlot() {}

    // ===== Getters / Setters =====
    public Long getId() { return id; }
    public Long getHostId() { return host != null ? host.getId() : null; }
    public Host getHost() { return host; }
    public void setHost(Host host) { this.host = host; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ===== Builder pattern (manual) =====
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AvailabilitySlot slot = new AvailabilitySlot();
        public Builder host(Host host) { slot.host = host; return this; }
        public Builder startTime(LocalDateTime t) { slot.startTime = t; return this; }
        public Builder endTime(LocalDateTime t) { slot.endTime = t; return this; }
        public Builder status(SlotStatus s) { slot.status = s; return this; }
        public AvailabilitySlot build() { return slot; }
    }
}
