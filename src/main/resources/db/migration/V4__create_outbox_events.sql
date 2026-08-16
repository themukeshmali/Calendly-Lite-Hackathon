-- =============================================
-- Migration V4: Create outbox_events table
-- =============================================
-- OUTBOX PATTERN: When a booking is saved, we ALSO insert a row here
-- in the SAME database transaction. A background worker picks up PENDING
-- events and sends notifications. If app crashes mid-booking, this event
-- is still saved and will be processed when the app restarts.

CREATE TABLE outbox_events (
    id                BIGSERIAL    PRIMARY KEY,
    event_type        VARCHAR(100) NOT NULL,  -- e.g. 'BOOKING_CONFIRMED', 'BOOKING_CANCELLED'
    payload           TEXT         NOT NULL,  -- JSON string with booking details
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count       INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_attempted_at TIMESTAMP,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_status ON outbox_events(status);

COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable notification delivery';
COMMENT ON COLUMN outbox_events.retry_count IS 'Number of failed send attempts; max 3 before moving to dead_letter_events';
