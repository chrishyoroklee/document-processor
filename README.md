# document-processor

Upload an invoice, receipt or purchase order. Claude extracts the key fields,
they're saved to Postgres, and a small React page lets you review and correct them.

## Run it locally

You need Java 21, Node 22 and Docker.

```bash
# 1. Database
docker compose up -d --wait

# 2. Backend on :8080 (uploads need a key; everything else works without one)
export ANTHROPIC_API_KEY=sk-ant-...
cd backend && ./mvnw spring-boot:run

# 3. Frontend on :5173, in another terminal
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 and upload `samples/invoice.pdf`.

## Run everything in Docker

```bash
export ANTHROPIC_API_KEY=sk-ant-...
docker compose --profile app up --build
```

Open http://localhost:8080. Spring serves the built React page and the API from one origin.

## Tests

```bash
cd backend && ./mvnw test
```

Needs Docker running (Testcontainers starts a throwaway Postgres). The Claude API
is mocked in tests, so no API key is needed and nothing is billed.

## API

| Method | Path | Does |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload a PDF/PNG/JPEG/WEBP (multipart field `file`), extract with Claude, save |
| `POST` | `/api/documents` | Create a document by hand (JSON) |
| `GET` | `/api/documents` | List documents, newest first |
| `GET` | `/api/documents/{id}` | One document with its line items |
| `PUT` | `/api/documents/{id}` | Replace with corrected values, mark `REVIEWED` |
| `DELETE` | `/api/documents/{id}` | Delete |

Errors use the RFC 9457 ProblemDetail format. Validation errors include an
`errors` map of field to message.

## Configuration

| Setting | Default | Environment variable |
|---|---|---|
| Claude API key | none | `ANTHROPIC_API_KEY` |
| Claude model | `claude-opus-5-5` | `CLAUDE_MODEL` |
| Database URL | `jdbc:postgresql://localhost:5432/docproc` | `DB_URL` |

Switching the model to `claude-haiku-5-5` makes extraction much cheaper and is
usually accurate enough for invoices.
