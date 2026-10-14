# ADR 0004: Enforce access control at retrieval time

## Status
Accepted

## Context
Some knowledge (internal procedures, escalation rules) is for staff only. If restricted text reaches the model,
no prompt can reliably stop it from appearing in an answer.

## Decision
Every chunk carries an `audience` metadata value. The caller's roles (from the Keycloak JWT) are turned into a
filter expression (`audience in ['public']` or `audience in ['public', 'staff']`) that is applied inside the vector
search. Intake draft replies always use public sources only, because they go to residents.

## Consequences
- Restricted chunks are never retrieved for public callers, so they can never be passed to the model.
- The evaluation set includes a case that fails if a staff source is retrieved for a public caller.
- Adding finer-grained audiences (per department, per program) is a metadata and role mapping change.
