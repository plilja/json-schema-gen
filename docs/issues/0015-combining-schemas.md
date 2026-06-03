# allOf / anyOf / oneOf

## What to build

Implement generation for schemas that use combining keywords: `allOf`,
`anyOf`, and `oneOf`.

## Acceptance criteria

- [ ] `allOf`: generated value satisfies all sub-schemas
- [ ] `anyOf`: generated value satisfies at least one sub-schema
- [ ] `oneOf`: generated value satisfies exactly one sub-schema
- [ ] Across repeated calls, different branches of `anyOf` and `oneOf`
      are exercised
- [ ] Integration tests validate output for each combining keyword
- [ ] `mvn test` passes

## Blocked by

#0014 — $ref resolution
