# String Constraints

## What to build

Extend string generation to respect `minLength`, `maxLength`, and
`pattern` constraints.

## Acceptance criteria

- [ ] `minLength` and `maxLength` are respected
- [ ] `pattern` (regex) constraint produces a matching string
- [ ] Boundary values for length (minLength, maxLength) are covered
      across repeated calls
- [ ] Integration tests validate output against constrained schemas
- [ ] Unit tests cover each constraint in isolation
- [ ] `mvn test` passes

## Blocked by

#0005 — String type generation
