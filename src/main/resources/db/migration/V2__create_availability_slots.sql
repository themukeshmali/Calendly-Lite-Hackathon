-- =============================================
-- Migration V2: Create availability_slots table
-- =============================================
-- Each slot is a time block a host makes available for booking

CREATE TABLE availability_slots (
    id         BIGSERIAL    PRIMARY KEY,
    host_id    BIGINT       NOT NULL REFERENCES hosts(id) ON DELETE CASCADE,
    start_time TIMESTAMP    NOT NULL,
    end_time   TIMESTAMP    NOT NULL,
    -- Status: OPEN = bookable, BOOKED = taken, CANCELLED = host removed it
    status     VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_slot_times CHECK (end_time > start_time),
    CONSTRAINT chk_slot_status CHECK (status IN ('OPEN', 'BOOKED', 'CANCELLED'))
);

CREATE INDEX idx_slots_host_status ON availability_slots(host_id, status);

COMMENT ON TABLE availability_slots IS 'Time blocks that hosts make available for booking';
