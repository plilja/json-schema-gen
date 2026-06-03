# Enum Exhaustiveness

## What to build

Implement generation for schemas with an `enum` keyword. Across repeated
calls every value in the enum must be produced.

## Acceptance criteria

- [ ] A schema with `enum` generates one of the listed values
- [ ] Across N repeated calls all enum values are produced
- [ ] Works for enums containing mixed types (string, integer, boolean,
      null)
- [ ] Integration test validates output against the schema
- [ ] Unit tests cover exhaustiveness in isolation
- [ ] `mvn test` passes

## Blocked by

#0005 — String type generation
