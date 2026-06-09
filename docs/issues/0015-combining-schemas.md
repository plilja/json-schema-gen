# allOf / anyOf / oneOf

## What to build

Implement generation for schemas that use combining keywords: `allOf`,
`anyOf`, and `oneOf`.

## Acceptance criteria

- [x] `allOf`: generated value satisfies all sub-schemas
- [x] `anyOf`: generated value satisfies at least one sub-schema
- [x] `oneOf`: generated value satisfies exactly one sub-schema
- [x] Across repeated calls, different branches of `anyOf` and `oneOf`
      are exercised
- [x] Integration tests validate output for each combining keyword
- [x] `mvn test` passes

## Blocked by

#0014 — $ref resolution
