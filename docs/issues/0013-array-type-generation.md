# Array Type Generation

## What to build

Implement generation for `{"type": "array"}` schemas, respecting
`minItems`, `maxItems`, and the `items` sub-schema.

## Acceptance criteria

- [x] `{"type": "array"}` generates a valid JSON array
- [x] `minItems` and `maxItems` constraints are respected
- [x] Each element is generated according to the `items` sub-schema
- [x] Boundary values for array length are covered across repeated calls
- [x] Integration tests validate output against the schema
- [x] Unit tests cover array generation in isolation
- [x] `mvn test` passes

## Blocked by

#0005 — String type generation
