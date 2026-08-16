-- =============================================
-- Migration V5: Create dead_letter_events table
-- =============================================
-- DEAD LETTER QUEUE: When an outbox event fails more than 3 times,
-- it is moved here instead of being silently dropped.
-- This makes failures visible and traceable — nothing is lost.

CREATE TABLE dead_letter_events (
    id                BIGSERIAL    PRIMARY KEY,
    original_event_id BIGINT       NOT NULL,   -- References the ID from outbox_events
    event_type        VARCHAR(100) NOT NULL,
    payload           TEXT         NOT NULL,
    failure_reason    TEXT,                     -- Last error message
    moved_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE dead_letter_events IS 'Parking lot for outbox events that failed all retry attempts';
COMMENT ON COLUMN dead_letter_events.original_event_id IS 'ID of the event in outbox_events that was moved here';
