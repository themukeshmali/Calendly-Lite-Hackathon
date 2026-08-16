package com.hackathon.calendlylite.repository;

import com.hackathon.calendlylite.entity.OutboxEvent;
import com.hackathon.calendlylite.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Used by the Outbox Worker (@Scheduled job) to find all events
     * that are waiting to be processed.
     * The worker picks these up every few seconds.
     */
    List<OutboxEvent> findByStatus(OutboxStatus status);
}
