# String Type Generation

## What to build

Implement generation for `{"type": "string"}` schemas. This is the first
concrete type generator and establishes the internal architecture patterns:
the schema model hierarchy, a sealed class dispatch mechanism, and the
generator structure that all subsequent generators will follow.

## Acceptance criteria

- [x] `{"type": "string"}` generates a valid JSON string value
- [x] The internal schema model has a sealed base type and a `StringSchema`
      concrete type
- [x] A dispatcher routes a parsed schema to the correct generator
      via a switch expression over the sealed hierarchy
- [x] Integration test validates output against the schema using the
      test helper from #0004
- [x] Unit tests cover string generation in isolation
- [x] `mvn test` passes

## Blocked by

#0004 — Integration test baseline
