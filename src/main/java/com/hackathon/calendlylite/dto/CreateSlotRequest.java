package com.hackathon.calendlylite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Request to add a bookable time slot")
public class CreateSlotRequest {

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    @Schema(description = "Slot start time (UTC)", example = "2026-08-14T10:00:00")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    @Schema(description = "Slot end time (UTC)", example = "2026-08-14T11:00:00")
    private LocalDateTime endTime;

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
