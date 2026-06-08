# String Constraints

## What to build

Extend string generation to respect `minLength`, `maxLength`, and
`pattern` constraints.

## Acceptance criteria

- [x] `minLength` and `maxLength` are respected
- [x] `pattern` (regex) constraint produces a matching string
- [x] Boundary values for length (minLength, maxLength) are covered
      across repeated calls
- [x] Integration tests validate output against constrained schemas
- [x] Unit tests cover each constraint in isolation
- [x] `mvn test` passes

## Blocked by

#0005 — String type generation
