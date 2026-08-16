package com.hackathon.calendlylite.repository;

import com.hackathon.calendlylite.entity.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {

    // Used by GET /admin/dead-letter-events to show all failures
    List<DeadLetterEvent> findAllByOrderByMovedAtDesc();
}
