# ADR 0003: Answer only from sources, cite them, and decline otherwise

## Status
Accepted

## Context
A wrong answer about a licence or registration rule can cost a resident a wasted trip or a fine. Language models
fill gaps with plausible text.

## Decision
- Retrieve first. If no chunk clears the similarity threshold, return a fixed "not found" message without
  calling the model.
- The system prompt requires every sentence to cite a numbered source and to return `NO_ANSWER` when the sources
  do not contain the answer.
- Citation numbers that do not match a retrieved source are removed. Answers without a valid citation are returned
  with `grounded=false` so the UI can show a warning and intake can route to a person.

## Consequences
- Out-of-scope questions are declined cheaply and predictably.
- Citations make answers checkable by residents and staff.
- A citation shows where an answer came from, not that it is correct. During testing, small models cited the right
  page and still moved a "six months" figure from one rule to another. The evaluation set therefore checks for
  known wrong facts (`forbidKeywords`) as well as expected ones, and the model choice is made from eval results.
