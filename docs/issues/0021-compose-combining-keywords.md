# Compose combining keywords on the same schema node

## What to build

A schema node may legally carry more than one combining keyword
(`oneOf`, `anyOf`, `allOf`) at once. JSON Schema treats each as an
independent constraint — all of them must hold for the generated value.

Today `JsonGenerator.buildDelegate` dispatches sequentially (oneOf →
anyOf → allOf) and the first match wins, so the others are silently
dropped. Example: `{"oneOf": [...], "allOf": [...]}` produces values
that satisfy `oneOf` but ignore `allOf`.

This issue introduces a way to compose all combining keywords present
on a single node so the generated value satisfies them simultaneously.

## Approaches considered

- Extend `SchemaMerger` to handle nested `oneOf` / `anyOf` / `allOf`
  branches (currently rejected as `IllegalArgumentException` — the
  three "unsupported composition" cases in `SchemaMerger`).
- Add an orchestrating generator that pulls all combining keywords
  off the parent up front, picks one branch from `oneOf`, one (or a
  subset) from `anyOf`, the full set from `allOf`, plus the parent
  core, and merges the lot.

## Acceptance criteria

- [ ] Schema with `anyOf` + `allOf` on the same node satisfies both
- [ ] Schema with `oneOf` + `allOf` on the same node satisfies both
- [ ] Schema with `oneOf` + `anyOf` on the same node satisfies both
- [ ] Schema with all three on the same node satisfies all three
- [ ] Integration tests cover each combination above
- [ ] `mvn test` passes

## Blocked by

#0015 — `allOf` / `anyOf` / `oneOf`
