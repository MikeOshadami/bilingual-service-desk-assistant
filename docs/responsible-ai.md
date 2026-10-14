# Responsible AI notes

A one-page summary of how this assistant is designed to be safe, fair and accountable. It is a portfolio project,
not a deployed service, but it is built the way a public-sector pilot would need to be.

## Purpose and scope

- Helps residents find information about driver's licences, photo ID cards and vehicle registration, and helps
  staff triage incoming requests.
- It gives information, not decisions. It cannot approve, deny or change anything about a licence or registration.
- Out of scope topics are declined.

## Human oversight

- Intake never replies to a resident automatically. The model drafts; a person reviews and sends.
- Cases go to human review when: classification confidence is low, the request is urgent, it is a complaint,
  no cited draft could be produced, or the input screen flagged it.

## Accuracy and transparency

- Answers come only from published public pages, with numbered citations and links so anyone can check them.
- If nothing relevant is found, the assistant says so instead of guessing.
- The UI shows whether an answer was cited, and a footer reminds people to confirm with Service New Brunswick.
- A fixed evaluation set measures accuracy, wrong facts, refusals, language and access control. It is re-run after
  any change to models, prompts, sources or thresholds.
- Known limitation: a citation shows where an answer came from, not that the model read it correctly. Small models
  were seen moving a number from one rule to another. This is why intake drafts are reviewed by a person and why
  the eval checks for known wrong facts.

## Privacy

- Models run locally with Ollama. Prompts and documents do not go to any external AI service.
- Personal identifiers are masked before logs and audit records are written.
- Draft replies are generated from a de-identified version of the question, so names and numbers do not enter
  retrieval or the audit trail.
- The assistant is instructed never to ask for or repeat personal information.
- Retention for audit records is configurable (see the runbook).

## Security

- Keycloak (OIDC) authentication; staff features need the `staff` role.
- Staff-only knowledge is filtered out inside the vector search, so it can never reach the model for a public caller.
- Prompt injection: pattern screening in English and French, untrusted text fenced as data, and the model has no
  tools or write access to any system.

## Official languages and accessibility

- English and French questions are answered in the language asked, from sources in either language (multilingual
  embeddings).
- The UI uses semantic HTML, keyboard-operable tabs, visible focus, live regions for results, and works in light
  and dark mode. A full WCAG 2.1 AA audit has not been done.

## Fairness

- The assistant answers from the same published rules for everyone and does not profile users.
- Bias testing is limited to checking that English and French questions get equivalent answers. A real deployment
  would add plain-language testing with residents, including newcomers and people using assistive technology.
