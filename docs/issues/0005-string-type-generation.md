# String Type Generation

## What to build

Implement generation for `{"type": "string"}` schemas. This is the first
concrete type generator and establishes the internal architecture patterns:
the schema model hierarchy, the `TypeGenerator` interface, and the
dispatch mechanism that all subsequent generators will follow.

## Acceptance criteria

- [ ] `{"type": "string"}` generates a valid JSON string value
- [ ] The `TypeGenerator` interface is defined in the `internal` package
- [ ] The internal schema model has a base type and a `StringSchema`
      concrete type
- [ ] A dispatcher routes a parsed schema to the correct generator
- [ ] Integration test validates output against the schema using the
      test helper from #0004
- [ ] Unit tests cover string generation in isolation
- [ ] `mvn test` passes

## Blocked by

#0004 — Integration test baseline
