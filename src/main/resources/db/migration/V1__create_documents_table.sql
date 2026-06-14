-- ============================================================
-- V1: Create documents table
--
-- WHY Flyway?
-- Every schema change is a versioned SQL file in Git.
-- Flyway tracks which migrations have run in the flyway_schema_history table.
-- On startup, Flyway applies any pending migrations in version order.
-- This means every developer and every environment gets the same schema.
--
-- NAMING CONVENTION: V{version}__{description}.sql
-- Double underscore between version and description.
-- Version can be: 1, 2, 1.1, 2.3.1 — Flyway sorts them numerically.
--
-- CRITICAL: NEVER modify a migration that has already run in any environment.
-- If you need to change something, create a NEW migration (V2__...).
-- Flyway checksums past migrations and will refuse to start if they change.
-- ============================================================

CREATE TABLE IF NOT EXISTS documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(255) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    file_size       BIGINT NOT NULL,
    content_type    VARCHAR(100),
    storage_path    VARCHAR(1000) NOT NULL,

    -- Using VARCHAR for enum values — matches @Enumerated(EnumType.STRING)
    -- WHY not a PostgreSQL ENUM type?
    -- PostgreSQL ENUMs require ALTER TYPE to add values — painful with Flyway.
    -- VARCHAR is more flexible; the constraint enforces valid values.
    status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',

    error_message   TEXT,
    total_chunks    INTEGER,
    processed_chunks INTEGER,

    -- WHY TIMESTAMPTZ (timestamp with timezone) not TIMESTAMP?
    -- Always store UTC in the DB. TIMESTAMPTZ stores UTC and converts
    -- to the session timezone on read. TIMESTAMP has no timezone info
    -- and causes "what timezone is this?" bugs when servers span regions.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_file_size CHECK (file_size > 0)
);

-- Indexes — match the ones defined on the @Table annotation
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_user_id ON documents(user_id);
CREATE INDEX idx_documents_created_at ON documents(created_at DESC);

-- Composite index for the common query: "user's documents sorted by date"
-- WHY composite? If user_id and created_at are always queried together,
-- a composite index is more efficient than two separate indexes.
CREATE INDEX idx_documents_user_created ON documents(user_id, created_at DESC);

-- Auto-update updated_at on any row change
-- WHY a trigger? Because application code can be bypassed (manual SQL, migrations).
-- The DB itself guarantees the timestamp is always current.
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
