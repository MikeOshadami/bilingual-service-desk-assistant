# Architecture

## Components

```mermaid
flowchart LR
    subgraph Mac["Your machine"]
        OLL["Ollama<br/>qwen2.5:7b (chat)<br/>bge-m3 (embeddings)"]
    end
    subgraph Docker["Docker Compose"]
        UI["Web UI<br/>(EN / FR)"]
        APP["Spring Boot app<br/>Spring AI"]
        PG[("PostgreSQL + pgvector<br/>chunks, audit_event")]
        KC["Keycloak<br/>OIDC, roles"]
        N8N["n8n<br/>workflow"]
        MAIL["Mailpit<br/>(test inbox)"]
        PROM["Prometheus"] --> GRAF["Grafana"]
        CASE["Case API<br/>(mock system of record)"]
    end
    WEB["Public gnb.ca pages"] -->|ingest| APP
    UI --> APP
    N8N -->|POST /api/requests| APP
    N8N --> MAIL
    APP --> OLL
    APP --> PG
    APP -->|validate JWT| KC
    APP --> CASE
    PROM -->|scrape /actuator/prometheus| APP
```

## Flow 1: answering a question (RAG with citations)

```mermaid
sequenceDiagram
    participant R as Resident
    participant A as App
    participant G as Input guard
    participant V as pgvector
    participant M as Ollama
    R->>A: POST /api/ask {question}
    A->>G: screen for prompt injection
    G-->>A: ok / blocked
    A->>A: detect language (en/fr)
    A->>M: embed question (bge-m3)
    A->>V: top-k search, filter audience in [caller's roles], score >= threshold
    alt nothing relevant
        A-->>R: "Not found, contact Service New Brunswick" (model not called)
    else sources found
        A->>M: system rules + numbered sources + question
        M-->>A: answer with [n] citations or NO_ANSWER
        A->>A: drop invalid citations, mark grounded
        A->>A: metrics + redacted audit event
        A-->>R: answer, sources, grounded flag
    end
```

## Flow 2: intake workflow (document processing and automation)

```mermaid
sequenceDiagram
    participant C as Channel (web, n8n webhook, upload)
    participant A as App
    participant M as Ollama
    participant K as Case API
    participant N as n8n
    C->>A: POST /api/requests (text or file)
    A->>A: extract text (Tika for PDF/Word)
    A->>A: input guard
    A->>M: structured extraction (JSON schema enforced)
    A->>A: validate fields, route to queue, set priority
    A->>M: RAG draft reply from public sources
    A->>A: review rules (low confidence, urgent, complaint, no grounded draft)
    A->>K: create case
    A-->>C: case id, queue, review flag, fields, draft
    C->>N: (n8n path) if needsHumanReview, email the queue
```

## Data model

- `vector_store` (created by Spring AI): chunk text, embedding (1024 dims, HNSW, cosine), metadata
  `source_id, title, url, lang, audience, topic, chunk, ingested_at`.
- `audit_event`: `occurred_at, actor, action, outcome, detail` (detail is redacted before insert).

## Trust boundaries

| Boundary | Control |
|---|---|
| Resident input to model | Pattern guard, input fenced as data in the prompt, model has no tools |
| Retrieved text to model | Fenced as `<sources>`, rule to ignore instructions inside sources |
| Staff-only knowledge | Audience filter applied inside the vector query, not after generation |
| Model output to resident | Citations validated, uncited answers flagged, intake drafts go to a person first |
| Logs and audit | PII redaction at the boundary |
| Model hosting | Local Ollama: prompts and documents never leave the machine |
