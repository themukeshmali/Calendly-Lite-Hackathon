-- =============================================
-- Migration V1: Create hosts table
-- =============================================
-- The HOST is the person who offers time slots (like a doctor, consultant, etc.)

CREATE TABLE hosts (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_host_email UNIQUE (email)
);

COMMENT ON TABLE hosts IS 'People who offer bookable time slots';
