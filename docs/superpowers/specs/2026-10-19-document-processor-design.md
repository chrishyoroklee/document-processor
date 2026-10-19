# Document Processor: Design

Date: 2026-10-19
Status: Approved in brainstorming, pending written review

## Purpose

An educational project for learning Java 21 and Spring Boot. Upload a business
document (invoice, receipt, purchase order), have Claude extract the key fields,
store them in Postgres, and review or correct them in a small React page.

Learning comes first. Every choice favors code that is easy to read and explain
over code that is clever or complete.

## How we build it

- **Approach:** something working end to end at every step. Each step adds one
  new concept and leaves a runnable app.
- **Who writes the code:** Claude builds every step, with one commit per step
  and explanatory comments, so the learner can walk the history step by step.
  (Originally the learner was going to write each step; changed at the
  learner's request.)

### Steps

| # | Step | Main concept |
|---|------|--------------|
| 1 | Generate the project, add `GET /api/health` | Spring Boot startup, `@RestController`, Maven |
| 2 | Postgres in docker-compose, Flyway migration for both tables | Schema design, migrations |
| 3 | JPA entities and repository; create, list, get, delete via API (no LLM) | `@Entity`, `@OneToMany`, Spring Data, records as DTOs |
| 4 | Testcontainers integration test for the API | JUnit 5, `@SpringBootTest`, real Postgres in tests |
| 5 | Upload endpoint that calls Claude and saves the result | `MultipartFile`, `RestClient`, Jackson, service layer, config properties |
| 6 | `PUT` to correct fields, validation, error handling | Bean Validation, `@RestControllerAdvice`, ProblemDetail |
| 7 | React page to upload, list and edit documents | CORS, connecting a frontend |
| 8 | Dockerfile and GitHub Actions CI | Multi-stage build, CI pipeline |

## Stack

- Java 21, Spring Boot 4.1 (3.x is past open-source support and no longer
  offered by Spring Initializr), Maven via the wrapper, `./mvnw`
- Repo layout: `backend/` (Spring Boot) and `frontend/` (React)
- Spring Web, Spring Data JPA, Bean Validation, Flyway
- PostgreSQL 16, run with docker-compose
- Claude API over plain HTTP using Spring's `RestClient` (no SDK, no Spring AI)
- JUnit 5, Testcontainers, Mockito
- React with Vite for the frontend
- Docker and GitHub Actions

### Defaults chosen

- Extraction is synchronous: the upload request waits for Claude's answer.
- The original file is not stored, only the extracted data.
- No authentication and no pagination.

## Architecture

```
React page (Vite, :5173)
      |  HTTP/JSON
      v
Spring Boot app (:8080) ---- HTTPS ---> Claude API
      |  JDBC
      v
PostgreSQL (:5432)
```

### Package layout

```
backend/src/main/java/dev/hyorok/docproc/
├── DocprocApplication.java
├── document/
│   ├── Document.java              @Entity, documents table
│   ├── LineItem.java              @Entity, line_items table
│   ├── DocumentType.java          enum
│   ├── DocumentStatus.java        enum
│   ├── DocumentRepository.java    Spring Data interface
│   ├── DocumentService.java       business logic, transactions
│   ├── DocumentController.java    REST endpoints
│   ├── DocumentNotFoundException.java
│   └── dto/                       request and response records
├── extraction/
│   ├── ClaudeExtractor.java       builds the request, parses the reply
│   ├── ExtractedDocument.java     record returned by the extractor
│   ├── ExtractedLineItem.java
│   ├── ClaudeProperties.java      @ConfigurationProperties record
│   └── ExtractionException.java
└── web/
    ├── HealthController.java      GET /api/health
    └── ApiExceptionHandler.java   @RestControllerAdvice
```

### Layer rules

- The controller handles HTTP only and never touches the repository.
- The service orchestrates: it calls the extractor, maps results to entities,
  and saves them. Write methods are `@Transactional`.
- The repository is the only database access.
- The `extraction` package has no dependency on JPA or the `document` package's
  entities. It takes bytes and a MIME type and returns an `ExtractedDocument`.
  It may reuse the `DocumentType` enum.
- Entities never leave the service layer. Controllers receive and return DTOs.

### Upload flow

1. `POST /api/documents/upload` receives a `MultipartFile`.
2. `DocumentService.upload(file)` checks the MIME type, then calls
   `ClaudeExtractor.extract(bytes, mimeType)`.
3. The service maps the `ExtractedDocument` to `Document` and `LineItem`
   entities, sets `status = EXTRACTED`, and saves.
4. The controller returns `201` with a `DocumentResponse`.

## Data model

Flyway migration `V1__create_documents.sql`:

```sql
CREATE TABLE documents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    document_type   VARCHAR(20)  NOT NULL
                    CHECK (document_type IN ('INVOICE','RECEIPT','PURCHASE_ORDER','OTHER')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'EXTRACTED'
                    CHECK (status IN ('EXTRACTED','REVIEWED')),
    vendor_name     VARCHAR(255),
    document_number VARCHAR(100),
    document_date   DATE,
    currency        CHAR(3),
    total_amount    NUMERIC(12,2),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE line_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    position     INT    NOT NULL,
    description  TEXT   NOT NULL,
    quantity     NUMERIC(12,3),
    unit_price   NUMERIC(12,2),
    amount       NUMERIC(12,2)
);

CREATE INDEX idx_line_items_document_id ON line_items(document_id);
```

Decisions:

- Money is `NUMERIC` in SQL and `BigDecimal` in Java, never floating point.
- Extracted fields are nullable. NULL means "not found in the document".
- Enums are stored as strings with `CHECK` constraints and mapped with
  `@Enumerated(EnumType.STRING)`.
- `position` preserves line order; `@OrderBy("position")` on the collection.
- Postgres does not index foreign keys automatically, so we add one.
- `created_at` and `updated_at` are set from Java with `@CreationTimestamp` and
  `@UpdateTimestamp`; the SQL defaults cover rows inserted by hand.
- JPA mapping: `Document` has `@OneToMany(mappedBy = "document",
  cascade = CascadeType.ALL, orphanRemoval = true)`; `LineItem` has
  `@ManyToOne(fetch = FetchType.LAZY)`.
- `spring.jpa.hibernate.ddl-auto=validate`: Flyway owns the schema, Hibernate
  only checks that entities match it.

## REST API

| Method | Path | Body | Success | Step |
|---|---|---|---|---|
| GET | `/api/health` | none | `200 {"status":"ok"}` | 1 |
| POST | `/api/documents` | JSON `DocumentRequest` | `201` + `DocumentResponse` | 3 |
| GET | `/api/documents` | none | `200` + `[DocumentSummary]`, newest first | 3 |
| GET | `/api/documents/{id}` | none | `200` + `DocumentResponse` | 3 |
| DELETE | `/api/documents/{id}` | none | `204` | 3 |
| PUT | `/api/documents/{id}` | JSON `DocumentRequest` | `200` + `DocumentResponse` | 6 |
| POST | `/api/documents/upload` | multipart field `file` | `201` + `DocumentResponse` | 5 |

- `POST /api/documents` is manual entry. Its `fileName` is set to `"manual"`
  and its status to `REVIEWED`, since a person typed it in.
- `PUT` replaces all header fields and the full line item list, and sets
  `status = REVIEWED`.
- The list endpoint returns `DocumentSummary` without line items, so it runs
  one query instead of N+1.

### DTOs

```java
public record DocumentRequest(
    @NotNull DocumentType documentType,
    String vendorName,
    String documentNumber,
    LocalDate documentDate,
    @Size(min = 3, max = 3) String currency,
    @PositiveOrZero BigDecimal totalAmount,
    @Valid List<LineItemRequest> lineItems) {}

public record LineItemRequest(@NotBlank String description,
    BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}

public record DocumentResponse(Long id, String fileName, DocumentType documentType,
    DocumentStatus status, String vendorName, String documentNumber,
    LocalDate documentDate, String currency, BigDecimal totalAmount,
    List<LineItemResponse> lineItems, Instant createdAt, Instant updatedAt) {}

public record LineItemResponse(Long id, int position, String description,
    BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}

public record DocumentSummary(Long id, String fileName, DocumentType documentType,
    DocumentStatus status, String vendorName, BigDecimal totalAmount,
    String currency, Instant createdAt) {}
```

A null `lineItems` in a request is treated as an empty list.

### Errors

All errors use Spring's `ProblemDetail` (RFC 9457) JSON shape.

| Situation | Status | Mechanism |
|---|---|---|
| Validation failure | 400 | `@Valid` on the controller parameter |
| Unknown id | 404 | `DocumentNotFoundException` mapped in `ApiExceptionHandler` |
| Unsupported file type | 415 | Service checks MIME type before calling Claude |
| File over 10 MB | 413 | `spring.servlet.multipart.max-file-size: 10MB` |
| Claude error, refusal, truncation, or unparseable reply | 502 | `ExtractionException` mapped in `ApiExceptionHandler` |

Accepted MIME types: `application/pdf`, `image/png`, `image/jpeg`, `image/webp`.

## Claude extraction

### Request

`POST https://api.anthropic.com/v1/messages` with headers:

```
x-api-key: <from config>
anthropic-version: 2023-06-01
anthropic-beta: server-side-fallback-2026-07-01
content-type: application/json
```

Body:

```json
{
  "model": "claude-opus-5-5",
  "max_tokens": 16000,
  "output_config": {
    "effort": "low",
    "format": { "type": "json_schema", "schema": { } }
  },
  "fallbacks": "default",
  "system": "You extract data from business documents. Use null for any field not present in the document. Never guess.",
  "messages": [{
    "role": "user",
    "content": [
      { "type": "document",
        "source": { "type": "base64", "media_type": "application/pdf", "data": "<base64>" } },
      { "type": "text", "text": "Extract the fields from this document." }
    ]
  }]
}
```

For images, the first block is `"type": "image"` with the image's `media_type`.
The base64 string must have no line breaks (use `Base64.getEncoder()`, not the
MIME encoder).

### Schema

Stored in `src/main/resources/extraction-schema.json` and loaded once at
startup. Field names are camelCase to match `ExtractedDocument` directly. Every
object has `additionalProperties: false`, every property is listed in
`required`, and optional values use `anyOf` with `{"type": "null"}`.
`documentType` is an `enum`; `documentDate` is a string with `format: date`;
amounts are `number`. Line items require `description` (string) and allow
`quantity`, `unitPrice`, `amount` to be null.

### Response handling

1. Non-2xx HTTP status: throw `ExtractionException`.
2. `stop_reason` is `refusal` or `max_tokens`: throw `ExtractionException`.
3. Take the first content block with `"type": "text"`, skipping thinking blocks.
4. Parse its text with Jackson into `ExtractedDocument`. A parse failure throws
   `ExtractionException`.

`RestClient` is built with a 120 second read timeout.

### Config

```yaml
claude:
  api-key: ${ANTHROPIC_API_KEY}
  base-url: https://api.anthropic.com
  model: claude-opus-5-5
  effort: low
```

Bound to `ClaudeProperties`. The API key comes only from the environment and is
never committed. Changing `model` to `claude-haiku-5-5` is supported as a
cheaper option. Expected cost on Opus at low effort is a few cents per page.

## Testing

- **Integration tests** (`@SpringBootTest` + Testcontainers Postgres) cover the
  REST endpoints through `MockMvc`: create, list, get, update, delete, 404,
  and 400 validation.
- `ClaudeExtractor` is replaced with `@MockitoBean` in integration tests, so
  tests never call the real API and CI needs no key.
- **Unit test** for `ClaudeExtractor` response parsing, using a saved sample
  response JSON in `src/test/resources`. Covers the happy path, `refusal`, and
  invalid JSON.
- Upload test: mocked extractor returns a fixed `ExtractedDocument`; assert it
  is saved with line items in order. Also assert 415 for a `text/plain` file.

## DevOps

- `docker-compose.yml` runs Postgres for local development, and from step 8
  also the app.
- Multi-stage `Dockerfile`: build with the Maven wrapper on a JDK image, run on
  a JRE image.
- GitHub Actions on every push: set up Java 21, run `./mvnw verify` (Docker is
  available on the runner for Testcontainers), and build the frontend.

## Out of scope

Authentication, pagination, storing original files, async processing, multiple
users, deployment to a cloud host.
