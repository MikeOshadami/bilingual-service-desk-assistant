# Runbook

Operational notes for running and supporting the assistant.

## Start and stop

```bash
ollama serve                 # if the Ollama app is not already running
docker compose up -d --build # whole stack
docker compose logs -f app   # follow the app
docker compose down          # stop (keeps data)
docker compose down -v       # stop and delete data volumes (knowledge base, audit, n8n)
```

## Health checks

| Check | Command | Healthy |
|---|---|---|
| App | `curl localhost:8080/actuator/health` | `"status":"UP"` with `db` UP |
| Ollama | `curl localhost:11434/api/tags` | lists `qwen2.5:7b` and `bge-m3` |
| Knowledge base | `curl localhost:8080/api/ingest/stats` (staff token if security is on) | `chunks` > 0 |
| Keycloak | `curl localhost:8081/realms/servicedesk/.well-known/openid-configuration` | JSON document |

## Common problems

| Symptom | Likely cause | Fix |
|---|---|---|
| `503 A dependency ... is unavailable` | Ollama not running or not reachable from Docker | Start Ollama; check `OLLAMA_BASE_URL` (Docker on Mac: `http://host.docker.internal:11434`) |
| First answer very slow, later ones fast | Model loading into memory | Normal. Keep Ollama running; `OLLAMA_KEEP_ALIVE=30m` keeps models loaded longer |
| Answers always "not found" | Empty knowledge base or threshold too high | Check `/api/ingest/stats`; reload with `POST /api/ingest/sources`; lower `RAG_SIMILARITY_THRESHOLD` and re-run the eval |
| A source shows `failed` on ingest | Page moved (404) or site unreachable | Update the URL in `data/sources.yaml`, then reload |
| `401` on staff endpoints | Missing or expired token (15 minutes) | Get a new token (see README) |
| `401` with a valid-looking token | Issuer mismatch | Token must come from `http://localhost:8081`; `KEYCLOAK_ISSUER` must match |
| `422 could not be processed automatically` on intake | Model output failed validation | The request should go to a person. If frequent, check the model and the eval report |
| Uncited answers (`grounded=false`) increasing | Model change, prompt change, or sources changed | Run the eval, compare with the last report |

## Updating knowledge

1. Edit `data/sources.yaml` (add, remove or fix a source).
2. Reload: `POST /api/ingest/sources` with a staff token. Each source's old chunks are replaced.
3. Run the evaluation and compare with the previous report before announcing the change.

## Changing the model

1. `ollama pull <model>`
2. Set `OLLAMA_CHAT_MODEL` in `.env`, restart the app.
3. Run the evaluation. Keep the model only if pass rate, grounded rate and latency are acceptable.

Changing the embedding model requires re-ingesting everything, and if the new model has a different vector size,
updating `spring.ai.vectorstore.pgvector.dimensions` and dropping the `vector_store` table.

## Audit

Staff can read recent events at `GET /api/audit?limit=100`. Details are redacted before storage. For retention,
schedule deletion of rows older than the agreed period, for example:

```sql
DELETE FROM audit_event WHERE occurred_at < now() - interval '90 days';
```
