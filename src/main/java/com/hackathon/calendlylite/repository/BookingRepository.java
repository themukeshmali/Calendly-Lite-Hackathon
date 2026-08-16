package com.hackathon.calendlylite.repository;

import com.hackathon.calendlylite.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * IDEMPOTENCY CHECK:
     * Before creating a new booking, we check if this idempotency key has been used before.
     * If it exists, we return the existing booking instead of creating a new one.
     */
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    // FIX BUG 9: Removed unused existsBySlot_Id() — double-booking is already prevented by
    // checking slot.getStatus() != OPEN inside BookingService, making this redundant.
}
