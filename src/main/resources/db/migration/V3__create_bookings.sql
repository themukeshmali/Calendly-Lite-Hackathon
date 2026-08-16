-- =============================================
-- Migration V3: Create bookings table
-- =============================================
-- Each booking links a guest to a specific time slot

CREATE TABLE bookings (
    id              BIGSERIAL    PRIMARY KEY,
    slot_id         BIGINT       NOT NULL REFERENCES availability_slots(id),
    guest_name      VARCHAR(255) NOT NULL,
    guest_email     VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    -- IDEMPOTENCY KEY: Unique token per booking request.
    -- If same key comes twice, we return the existing booking instead of creating a duplicate.
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_booking_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_booking_slot        UNIQUE (slot_id),   -- DB-level: one booking per slot max
    CONSTRAINT chk_booking_status     CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_bookings_guest_email ON bookings(guest_email);

COMMENT ON TABLE bookings IS 'Guest reservations for time slots';
COMMENT ON COLUMN bookings.idempotency_key IS 'Prevents duplicate bookings on retry; client generates UUID per request';
