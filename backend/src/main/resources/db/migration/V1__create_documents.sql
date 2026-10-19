-- Flyway runs every file in db/migration exactly once, in version order (V1, V2, ...),
-- and records it in the flyway_schema_history table. Never edit a migration that has
-- already run: add a new V2__... file instead.

CREATE TABLE documents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    document_type   VARCHAR(20)  NOT NULL
                    CHECK (document_type IN ('INVOICE', 'RECEIPT', 'PURCHASE_ORDER', 'OTHER')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'EXTRACTED'
                    CHECK (status IN ('EXTRACTED', 'REVIEWED')),
    -- Extracted fields are nullable: NULL means "not found in the document".
    vendor_name     VARCHAR(255),
    document_number VARCHAR(100),
    document_date   DATE,
    currency        CHAR(3),                 -- ISO 4217 code: USD, EUR, KRW
    total_amount    NUMERIC(12, 2),          -- NUMERIC, never FLOAT, for money
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE line_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    position    INT    NOT NULL,             -- SQL rows have no order, so we store it
    description TEXT   NOT NULL,
    quantity    NUMERIC(12, 3),
    unit_price  NUMERIC(12, 2),
    amount      NUMERIC(12, 2)
);

-- Postgres does not index foreign keys automatically.
CREATE INDEX idx_line_items_document_id ON line_items (document_id);
