# Bilingual Service Desk Assistant

A local-first AI assistant for public-service requests, built with **Java 21, Spring Boot 3.5, Spring AI and
Ollama**. It answers residents' questions in English or French from published New Brunswick public pages with
citations, reads incoming emails and documents into structured data, routes them to the right queue with a draft
reply for staff, and records everything in a redacted audit trail.

Every model runs on your own machine. No prompt, document or personal detail is sent to an external AI service.

> Portfolio project. Not affiliated with or endorsed by the Government of New Brunswick. The knowledge base is
> built from publicly available pages; answers may be out of date.

## What it does

| Capability | How it works |
|---|---|
| **Knowledge retrieval (RAG)** | Public pages (HTML or PDF) are cleaned, split into chunks, embedded with `bge-m3` and stored in PostgreSQL/pgvector. Answers cite numbered sources; nothing relevant found means the model is not called. |
| **Bilingual** | Questions are answered in the language asked. Multilingual embeddings let a French question match an English source and the reverse. The UI switches between English and French. |
| **Document processing** | Emails, letters, PDFs and Word files go through Apache Tika, then Ollama structured outputs fill a JSON schema (category, contact details, reference numbers, urgency, missing information, a de-identified question). Output is validated before use. |
| **Workflow automation** | `/api/requests` extracts, routes to a queue, sets priority, drafts a cited reply from public sources and creates a case in a case management API. An **n8n** workflow exposes it as a webhook and emails the queue when human review is needed. |
| **Security** | Keycloak (OIDC/JWT). Staff-only knowledge is filtered out inside the vector query, so it never reaches the model for a public caller. |
| **Privacy and safety** | Local models, PII redaction for logs and audit, prompt-injection screening (EN/FR), untrusted text fenced as data, human review rules for intake. |
| **Monitoring** | Micrometer metrics and Spring AI observations (latency, tokens, grounded rate, guard blocks, cases by queue) in Prometheus and a provisioned Grafana dashboard. |
| **Evaluation** | A 16-case question set checks cited sources, expected facts, known wrong facts, refusals, language and access control, and writes a report. |

See [docs/architecture.md](docs/architecture.md) for diagrams and [docs/adr](docs/adr) for design decisions.

## Quick start (macOS)

Needs Docker Desktop and about 16 GB of RAM for the 7B model (use `qwen2.5:3b` on 8 GB machines).

```bash
# 1. Ollama runs natively so it can use the Apple GPU
brew install ollama          # or download the app from ollama.com
ollama serve &               # skip if the Ollama app is already running
ollama pull qwen2.5:7b
ollama pull bge-m3

# 2. Start the stack
cp .env.example .env
docker compose up -d --build

# 3. Open the assistant
open http://localhost:8080
```

On first start the app loads the pages in `data/sources.yaml` (about one to two minutes). Watch it with
`docker compose logs -f app`.

| Service | URL |
|---|---|
| Assistant UI and API | http://localhost:8080 |
| Keycloak admin (admin / admin) | http://localhost:8081 |
| n8n | http://localhost:5678 |
| Mailpit (test inbox) | http://localhost:8025 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

### Run the app from your IDE instead

```bash
docker compose up -d postgres
./mvnw spring-boot:run       # security off, Ollama at localhost:11434
```

## Try it

```bash
# Ask in English or French
curl -s localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"Can I renew my photo ID card online?"}' | jq

curl -s localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"Combien de temps dure le programme de permis progressif?"}' | jq '.answer, .sources[].title'

# Submit an email or a document to the intake workflow
curl -s localhost:8080/api/requests -F file=@data/samples/immatriculation-fr.txt | jq
curl -s localhost:8080/api/requests -F file=@data/samples/injection-attempt.txt | jq '.needsHumanReview, .reviewReasons'

# Extraction only (no case created)
curl -s localhost:8080/api/extract -F file=@data/samples/renewal-email-en.txt | jq
```

### Staff features (security on)

Demo users are defined in `infra/keycloak/servicedesk-realm.json` (local use only).

```bash
TOKEN=$(curl -s -X POST localhost:8081/realms/servicedesk/protocol/openid-connect/token \
  -d grant_type=password -d client_id=servicedesk-cli \
  -d username=staff.demo -d password=staff-demo-pass | jq -r .access_token)

# Staff can retrieve the internal (fictional) escalation procedure; the public cannot
curl -s localhost:8080/api/ask -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"What internal tag is used for privacy incidents?"}' | jq '.answer'

curl -s localhost:8080/api/audit?limit=20 -H "Authorization: Bearer $TOKEN" | jq   # redacted audit trail
curl -s -X POST localhost:8080/api/ingest/sources -H "Authorization: Bearer $TOKEN" | jq   # reload sources
curl -s localhost:8080/mock-case/api/cases | jq                                         # cases created
```

### n8n workflow

1. Open http://localhost:5678 and create the local owner account.
2. Import the workflow:
   `docker compose exec n8n n8n import:workflow --input=/workflows/service-request-intake.json`
3. Open **Service request intake**, edit **Notify review queue**, and create an SMTP credential:
   host `mailpit`, port `1025`, SSL/TLS off, no user or password.
4. Activate (publish) the workflow, then send a request:

```bash
curl -s localhost:5678/webhook/service-request -H 'Content-Type: application/json' \
  -d '{"text":"Bonjour, ma plaque a été volée hier soir et je dois aller travailler demain. Que dois-je faire? Marie Cormier"}' | jq
```

Cases that need review show up as emails at http://localhost:8025.

## Evaluation

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=eval     # writes eval/report.md
```

Each case in [eval/questions.yaml](eval/questions.yaml) checks:

- **Retrieval and grounding**: the expected source was cited.
- **Facts**: expected facts appear and **known wrong facts do not**. A citation alone does not prove an answer is
  right, so this catches a model that cites the correct page and still misreads it.
- **Refusals**: out-of-scope questions and injection attempts are declined.
- **Access control**: staff-only sources never appear for a public caller.
- **Language**: the reply is in the language asked.

Re-run it after changing the model, prompts, chunk size, threshold or sources, and compare reports.

### Results so far

Sample run with `qwen2.5:3b` on a CPU-only machine (full report: [eval/sample-report-qwen2.5-3b.md](eval/sample-report-qwen2.5-3b.md)):

| Area | Result |
|---|---|
| Overall | 12 / 16 cases passed |
| Refusals, injection screening, staff-only access control | 6 / 6 passed |
| Answerable questions with a valid citation | 11 / 12 |

What the failures showed:

- **Cited but wrong.** For "How long can I drive on my Ontario licence?" the page says new residents must get a New
  Brunswick licence *as soon as* they take up residence. Small models cited the right page and still answered with a
  number taken from a different rule on it ("six months", "four years"). `qwen2.5:7b` got the key fact right in
  testing. This is why the eval checks facts and known wrong facts, not just citations, and why intake drafts are
  always reviewed by a person.
- **Invented detail.** On inspection cost the model gave the right $45 fee but added an invented fee for heavier
  vehicles and did not cite a source, so the answer was flagged as ungrounded.
- **Over-refusal.** An earlier prompt made small models reply "not found" even when the answer was in the top
  source. Changing the refusal rule to "only when no source is about the topic" fixed it without breaking the
  out-of-scope cases. This was found with the eval, not by eyeballing.
- One French failure was a correct answer that the keyword check missed; the check has been fixed.

Run the eval with your own model (on Apple silicon `qwen2.5:7b` answers in a few seconds) and commit the report
next to this one to compare.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server |
| `OLLAMA_CHAT_MODEL` | `qwen2.5:7b` | Chat model (try `llama3.1:8b`, `qwen3:8b`, `mistral`) |
| `OLLAMA_EMBEDDING_MODEL` | `bge-m3` | Multilingual embeddings, 1024 dimensions |
| `SECURITY_ENABLED` | `false` (`true` in Compose) | Enforce Keycloak JWTs on staff endpoints |
| `RAG_SIMILARITY_THRESHOLD` | `0.45` | Minimum similarity for a chunk to be used |
| `SOURCES_FILE` | `data/sources.yaml` | Knowledge sources |
| `INGEST_ON_STARTUP` | `true` | Load sources when the knowledge base is empty |
| `CASE_API_BASE_URL` | built-in mock | Case management system to create cases in |

## API

| Method and path | Access | Purpose |
|---|---|---|
| `POST /api/ask` | public | Answer a question with citations |
| `POST /api/requests` (JSON or multipart) | public | Full intake workflow, creates a case |
| `POST /api/extract` (JSON or multipart) | public | Structured extraction only |
| `POST /api/ingest/sources` | staff | Reload all sources |
| `POST /api/ingest/file` | staff | Add one document (choose audience `public` or `staff`) |
| `GET /api/ingest/stats` | staff | Chunk count |
| `GET /api/audit` | staff | Recent audit events (redacted) |
| `GET /actuator/health`, `/actuator/prometheus` | public | Health and metrics |

## Project layout

```
src/main/java/ca/oshadami/servicedesk/
  rag/        retrieval, prompts, citation checks
  ingest/     source catalog, HTML/PDF cleaning, chunking and embedding
  extract/    structured extraction with a JSON schema
  workflow/   triage rules, case management connector, mock case API
  config/     security (Keycloak roles), audiences, settings
  guard/      prompt injection screening
  privacy/    PII redaction
  audit/      audit trail
  eval/       offline evaluation runner
data/         sources.yaml, staff-only sample procedure, sample requests
eval/         evaluation questions and reports
infra/        Keycloak realm, n8n workflow, Prometheus, Grafana
docs/         architecture, ADRs, runbook, Responsible AI notes
```

## Tests

```bash
./mvnw test
```

Unit tests cover PII redaction, injection screening (English and French), language detection, citation parsing,
HTML cleaning, source validation, Keycloak role mapping, the audience filter, extraction clean-up and evaluation
scoring.

## Limitations and next steps

- Keyword checks in the evaluation are a simple proxy for correctness. Next: add an LLM-as-judge faithfulness check
  and human-labelled answers.
- Scanned image PDFs need OCR (Tika with Tesseract) before extraction.
- The case management API is a mock. The connector is a plain REST client, so pointing it at a real system is
  a configuration change plus field mapping.
- Sign-in for the web UI itself is not built yet; staff features are used through the API with a token.
- Add a Power Automate or Logic Apps variant of the intake flow to show the same pattern on Microsoft's low-code stack.

## Documentation

- [Architecture](docs/architecture.md)
- [Design decisions](docs/adr)
- [Runbook](docs/runbook.md)
- [Responsible AI notes](docs/responsible-ai.md)
