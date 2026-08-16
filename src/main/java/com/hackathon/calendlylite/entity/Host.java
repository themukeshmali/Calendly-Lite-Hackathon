package com.hackathon.calendlylite.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a host — the person who offers bookable time slots.
 */
@Entity
@Table(name = "hosts")
public class Host {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AvailabilitySlot> slots = new ArrayList<>();

    // ===== Constructors =====
    public Host() {}

    public Host(String name, String email) {
        this.name = name;
        this.email = email;
    }

    // ===== Getters / Setters =====
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<AvailabilitySlot> getSlots() { return slots; }
}
