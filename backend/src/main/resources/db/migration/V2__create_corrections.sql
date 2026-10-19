-- Step 9: the app learns from reviewer corrections.
-- A new migration file, never an edit to V1: databases that already ran V1
-- (yours, CI's, a teammate's) pick up only this file on the next start.

-- One row per field a reviewer changed on a document Claude had extracted.
CREATE TABLE corrections (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- SET NULL, not CASCADE: deleting a document shouldn't make the app forget
    -- what it learned from it.
    document_id     BIGINT REFERENCES documents (id) ON DELETE SET NULL,
    -- The corrected vendor name, so we know which vendor the lesson applies to.
    vendor_name     VARCHAR(255),
    field_name      VARCHAR(50) NOT NULL,
    -- Values of any type are stored as text: "2026-10-03", "162.00", "INVOICE".
    extracted_value TEXT,
    corrected_value TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_corrections_created_at ON corrections (created_at);

-- Join table for a many-to-many relationship: a document can use several past
-- corrections, and one correction can help many later documents.
CREATE TABLE document_applied_corrections (
    document_id   BIGINT NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    correction_id BIGINT NOT NULL REFERENCES corrections (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, correction_id)
);
