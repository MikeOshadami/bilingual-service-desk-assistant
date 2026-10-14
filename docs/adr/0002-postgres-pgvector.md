# ADR 0002: PostgreSQL with pgvector as the vector store

## Status
Accepted

## Context
The assistant needs similarity search over a few thousand chunks with metadata filters (audience, language,
topic). Options considered: a dedicated vector database, an in-memory store, PostgreSQL with pgvector.

## Decision
Use PostgreSQL with the pgvector extension, an HNSW index and cosine distance.

## Consequences
- One database for vectors and the audit trail; standard backup, access control and operations.
- Metadata filters run inside the same query as the similarity search, which is what makes role-aware retrieval
  enforceable.
- PostgreSQL is a familiar, supportable platform for most IT organisations.
- At much larger scale a dedicated engine may be faster, but that is not a constraint here.
