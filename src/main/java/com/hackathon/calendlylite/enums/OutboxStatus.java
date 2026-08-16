package com.hackathon.calendlylite.enums;

/**
 * Status of an outbox event (notification task).
 * PENDING  = Waiting to be sent
 * SENT     = Successfully delivered
 * FAILED   = Moved to dead-letter after max retries
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
