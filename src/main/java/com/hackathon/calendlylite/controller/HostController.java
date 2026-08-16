package com.hackathon.calendlylite.controller;

import com.hackathon.calendlylite.dto.ApiResponse;
import com.hackathon.calendlylite.dto.CreateHostRequest;
import com.hackathon.calendlylite.dto.CreateSlotRequest;
import com.hackathon.calendlylite.entity.AvailabilitySlot;
import com.hackathon.calendlylite.entity.Host;
import com.hackathon.calendlylite.service.HostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hosts")
@Tag(name = "Hosts & Slots", description = "Manage hosts and their available time slots")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }

    @GetMapping
    @Operation(summary = "List all hosts")
    public ResponseEntity<ApiResponse<List<Host>>> getAllHosts() {
        return ResponseEntity.ok(ApiResponse.ok("All hosts", hostService.getAllHosts()));
    }

    @PostMapping
    @Operation(summary = "Register a new host")
    public ResponseEntity<ApiResponse<Host>> createHost(@Valid @RequestBody CreateHostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Host created", hostService.createHost(request)));
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get host by ID")
    public ResponseEntity<ApiResponse<Host>> getHost(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Host found", hostService.getHost(id)));
    }

    @PostMapping("/{id}/availability")
    @Operation(summary = "Add a time slot", description = "Host adds an available time block. Times in UTC, must be future.")
    public ResponseEntity<ApiResponse<AvailabilitySlot>> addSlot(
            @PathVariable Long id, @Valid @RequestBody CreateSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Slot added", hostService.addSlot(id, request)));
    }

    @GetMapping("/{id}/slots")
    @Operation(summary = "List open slots (Redis cached, 5 min TTL)")
    public ResponseEntity<ApiResponse<List<AvailabilitySlot>>> getOpenSlots(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Open slots", hostService.getOpenSlots(id)));
    }
}
