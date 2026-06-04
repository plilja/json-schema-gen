# ArchUnit Baseline

## What to build

Establish ArchUnit rules that enforce the structural conventions of the
codebase. These rules act as automated guardrails for all future development.

## Rules

1. **Layered architecture** — `api` may access all layers; `parser` and
   `generator` may only access `model`; `model` is a leaf with no internal
   dependencies.
2. **No dependency cycles** between packages.
3. **Jackson allowed only in `parser` and `model`** — all other packages,
   including `api`, must not depend on `com.fasterxml.jackson`.

See ADR-0003 for the rationale behind these decisions.

## Acceptance criteria

- [x] ArchUnit dependency added as a test-scoped dependency
- [x] All rules pass with `mvn test`

## Blocked by

#0002 — Basic API entry point
