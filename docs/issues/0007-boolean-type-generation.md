# Boolean Type Generation

## What to build

Implement generation for `{"type": "boolean"}` schemas. Across repeated
calls both `true` and `false` must be produced.

## Acceptance criteria

- [ ] `{"type": "boolean"}` generates a valid JSON boolean value
- [ ] Across N repeated calls both `true` and `false` are produced
- [ ] Integration test validates output against the schema
- [ ] Unit tests cover exhaustiveness in isolation
- [ ] `mvn test` passes

## Blocked by

#0005 — String type generation
