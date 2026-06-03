# ArchUnit Baseline

## What to build

Establish ArchUnit rules that enforce the structural conventions of the
codebase. These rules act as automated guardrails for all future development.

## Acceptance criteria

- [ ] ArchUnit dependency added as a test-scoped dependency
- [ ] Rule: classes in `internal` packages are not referenced from `api`
      packages or by consumers (no leaking internals)
- [ ] Rule: classes in `api` do not depend on third-party library types
      (Jackson, Gson, etc.)
- [ ] All rules pass with `mvn test`

## Blocked by

#0002 — Basic API entry point
