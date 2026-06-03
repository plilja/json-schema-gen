# Integer Type Generation with Boundary Values

## What to build

Implement generation for `{"type": "integer"}` schemas, including
`minimum` and `maximum` constraints. Across repeated calls the generator
must cover boundary values: min, max, min+1, and max-1.

## Acceptance criteria

- [ ] `{"type": "integer"}` generates a valid JSON integer value
- [ ] `minimum` and `maximum` constraints are respected
- [ ] Across N repeated calls, the values min, max, min+1, and max-1
      are all produced (where constraints are set)
- [ ] Integration test validates output against the schema
- [ ] Unit tests cover boundary value exhaustiveness in isolation
- [ ] `mvn test` passes

## Blocked by

#0005 — String type generation
