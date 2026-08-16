package com.hackathon.calendlylite.controller;

import com.hackathon.calendlylite.dto.ApiResponse;
import com.hackathon.calendlylite.entity.DeadLetterEvent;
import com.hackathon.calendlylite.entity.OutboxEvent;
import com.hackathon.calendlylite.repository.DeadLetterEventRepository;
import com.hackathon.calendlylite.repository.OutboxEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Observability — view outbox and dead letter events")
public class AdminController {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    public AdminController(DeadLetterEventRepository deadLetterEventRepository,
                           OutboxEventRepository outboxEventRepository) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @GetMapping("/dead-letter-events")
    @Operation(summary = "Dead Letter Queue — events that failed all 3 retries")
    public ResponseEntity<ApiResponse<List<DeadLetterEvent>>> getDeadLetterEvents() {
        List<DeadLetterEvent> events = deadLetterEventRepository.findAllByOrderByMovedAtDesc();
        return ResponseEntity.ok(ApiResponse.ok("Dead letter events: " + events.size(), events));
    }

    @GetMapping("/outbox-events")
    @Operation(summary = "All outbox events — view full notification lifecycle")
    public ResponseEntity<ApiResponse<List<OutboxEvent>>> getOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok("Outbox events: " + events.size(), events));
    }
}
