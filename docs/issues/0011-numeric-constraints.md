# Numeric Constraints

## What to build

Extend integer generation to support `exclusiveMinimum`,
`exclusiveMaximum`, and `multipleOf` constraints.

## Acceptance criteria

- [ ] `exclusiveMinimum` and `exclusiveMaximum` are respected
- [ ] `multipleOf` produces values that are multiples of the given number
- [ ] Boundary values are covered across repeated calls where applicable
- [ ] Integration tests validate output against constrained schemas
- [ ] Unit tests cover each constraint in isolation
- [ ] `mvn test` passes

## Blocked by

#0006 — Integer type generation with boundary values
