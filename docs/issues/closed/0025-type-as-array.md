# `type` as an array of types

## What to build

JSON Schema Draft 7 allows `type` to be either a single string or an
array of strings, where the array form means "value must be of one of
these types". The most common case in the wild is nullability:

```json
{ "type": ["string", "null"] }
{ "type": ["integer", "string"] }
```

Today the parser rejects these — Jackson tries to deserialize the array
into a single schema model class and fails with
`MismatchedInputException: Cannot deserialize value of type UntypedSchema
from Array value`.

The parser needs to recognise `type` as either a scalar or an array, and
the model + generator need to treat the array form as an implicit
`oneOf` over the listed types, applying any other constraints on the
node (e.g. `minLength`, `minimum`) to whichever branch they're relevant
to.

## Acceptance criteria

- [x] `{"type": ["string", "null"]}` parses and generates values that
      are sometimes strings and sometimes `null`
- [x] `{"type": ["integer", "string"], "minLength": 3}` generates either
      an integer or a string of length ≥ 3 (constraints apply to the
      branch they're meaningful for; irrelevant constraints are ignored)
- [x] Single-string `type` continues to work exactly as today
- [x] Cross-cutting keywords (`enum`, `const`, `if`/`then`/`else`,
      combining keywords) still apply over the union
- [x] `mvn verify` passes

## Blocked by

#0008 — Null type generation
#0015 — Combining schemas (anyOf/oneOf/allOf)
