# Array Type Generation

## What to build

Implement generation for `{"type": "array"}` schemas, respecting
`minItems`, `maxItems`, and the `items` sub-schema.

## Acceptance criteria

- [ ] `{"type": "array"}` generates a valid JSON array
- [ ] `minItems` and `maxItems` constraints are respected
- [ ] Each element is generated according to the `items` sub-schema
- [ ] Boundary values for array length are covered across repeated calls
- [ ] Integration tests validate output against the schema
- [ ] Unit tests cover array generation in isolation
- [ ] `mvn test` passes

## Blocked by

#0005 — String type generation
