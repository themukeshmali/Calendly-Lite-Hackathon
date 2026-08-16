package com.hackathon.calendlylite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to book a time slot")
public class BookingRequest {

    @NotBlank(message = "Guest name is required")
    @Schema(description = "Name of the guest", example = "Mukesh Mali")
    private String guestName;

    @NotBlank(message = "Guest email is required")
    @Email(message = "Must be a valid email address")
    @Schema(description = "Email of the guest", example = "mukesh@example.com")
    private String guestEmail;

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }
}
