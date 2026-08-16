package com.hackathon.calendlylite.repository;

import com.hackathon.calendlylite.entity.AvailabilitySlot;
import com.hackathon.calendlylite.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    // Get all OPEN slots for a specific host (used in GET /hosts/{id}/slots)
    List<AvailabilitySlot> findByHost_IdAndStatus(Long hostId, SlotStatus status);

    /**
     * Checks whether a host already has a non-cancelled slot that overlaps with
     * the proposed [startTime, endTime] window. Prevents the host from creating
     * conflicting availability blocks.
     *
     * The :cancelled parameter is typed as SlotStatus (not a raw string) because
     * Hibernate 6 JPQL requires enum comparisons via Java enum references, not literals.
     */
    @Query("""
        SELECT COUNT(s) > 0 FROM AvailabilitySlot s
        WHERE s.host.id = :hostId
          AND s.status <> :cancelled
          AND s.startTime < :endTime
          AND s.endTime > :startTime
    """)
    boolean existsOverlappingSlot(
            @Param("hostId") Long hostId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("cancelled") SlotStatus cancelled
    );
}
