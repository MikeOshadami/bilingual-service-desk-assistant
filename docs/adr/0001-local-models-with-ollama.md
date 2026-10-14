# ADR 0001: Run models locally with Ollama

## Status
Accepted

## Context
Requests to a public service desk contain personal information: names, addresses, licence numbers, sometimes
health or financial details. Sending this to a third-party AI API raises data residency, privacy impact
assessment and procurement questions before a single pilot can start.

## Decision
Run the chat and embedding models locally with Ollama. The application talks to Ollama through Spring AI's
portable `ChatClient` and `EmbeddingModel` interfaces.

## Consequences
- Prompts, documents and embeddings never leave the machine or data centre.
- No per-token cost; capacity is bounded by local hardware instead.
- Smaller open models are less accurate than frontier models. This is managed with retrieval, strict prompts,
  citation checks, human review for intake, and an evaluation set that measures quality per model.
- Because the code depends on Spring AI interfaces, moving to Azure OpenAI or another approved provider is a
  dependency and configuration change, not a rewrite.
