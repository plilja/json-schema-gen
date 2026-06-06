# Boolean Type Generation

## What to build

Implement generation for `{"type": "boolean"}` schemas. Across repeated
calls both `true` and `false` must be produced.

## Acceptance criteria

- [x] `{"type": "boolean"}` generates a valid JSON boolean value
- [x] Across N repeated calls both `true` and `false` are produced
- [x] Integration test validates output against the schema
- [x] Unit tests cover exhaustiveness in isolation
- [x] `mvn test` passes

## Blocked by

#0005 — String type generation
