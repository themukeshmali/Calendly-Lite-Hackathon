package com.hackathon.calendlylite.enums;

/**
 * Status of a time slot.
 * OPEN       = Available for booking
 * BOOKED     = Already reserved by a guest
 * CANCELLED  = Host removed this slot
 */
public enum SlotStatus {
    OPEN,
    BOOKED,
    CANCELLED
}
