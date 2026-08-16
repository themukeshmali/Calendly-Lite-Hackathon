package com.hackathon.calendlylite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to register a new host")
public class CreateHostRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Full name of the host", example = "Dr. Priya Sharma")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Schema(description = "Email address", example = "priya@clinic.com")
    private String email;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
