# ADR-0003: Package Layering Conventions

## Status
Accepted

## Context
The codebase is divided into a public `api` package, a public `errors` package,
and an `internal` implementation split across `parser`, `model`, and `generator`
subpackages. Without explicit rules, dependencies between these packages can
drift over time, making the codebase harder to understand and maintain.

## Decision
Enforce the following layering:

```
api          — entry point; may access parser, generator, model, and errors
parser       — may only access model and errors
generator    — may only access model and errors
model        — leaf; no dependencies on other internal packages
errors       — leaf; public exception types; no dependencies on other packages
```

Additionally, Jackson (`com.fasterxml.jackson`) is allowed only in `parser` and
`model`. All other packages — including `api` and `errors` — must not depend on
Jackson.

These rules are enforced as automated tests using ArchUnit.

## Rationale
- `model` as a leaf keeps the schema representation free of parsing and generation
  concerns, making it easy to reason about in isolation.
- Keeping `parser` and `generator` independent of each other means either can be
  replaced without affecting the other.
- `errors` is a separate leaf package so internal packages can throw public
  exceptions directly without depending on `api`, and so consumers have one
  obvious place to import exception types from.
- Restricting Jackson to `parser` and `model` preserves the guarantee that the
  public API is free of third-party types, as established in ADR-0001.

## Consequences
- Any future code that violates the layering or Jackson scope will cause the
  ArchUnit tests to fail, giving immediate feedback.
- New internal subpackages must be consciously placed in the layer diagram.
