package com.hackathon.calendlylite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hackathon.calendlylite.enums.BookingStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private AvailabilitySlot slot;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "guest_email", nullable = false)
    private String guestEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ===== Constructors =====
    public Booking() {}

    // ===== Getters / Setters =====
    public Long getId() { return id; }
    public Long getSlotId() { return slot != null ? slot.getId() : null; }
    public AvailabilitySlot getSlot() { return slot; }
    public void setSlot(AvailabilitySlot slot) { this.slot = slot; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String key) { this.idempotencyKey = key; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ===== Builder =====
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Booking b = new Booking();
        public Builder slot(AvailabilitySlot slot) { b.slot = slot; return this; }
        public Builder guestName(String name) { b.guestName = name; return this; }
        public Builder guestEmail(String email) { b.guestEmail = email; return this; }
        public Builder status(BookingStatus s) { b.status = s; return this; }
        public Builder idempotencyKey(String key) { b.idempotencyKey = key; return this; }
        public Booking build() { return b; }
    }
}
