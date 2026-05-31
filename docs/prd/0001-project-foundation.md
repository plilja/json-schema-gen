# PRD-0001: Project Foundation — JSON Schema Test Data Generator

## Problem Statement

Writing test fixtures by hand is tedious and error-prone. Developers working
with JSON-based APIs typically craft JSON payloads manually, which means edge
cases and boundary conditions are often missed. There is no Java-native library
that takes a JSON schema and automatically generates valid, constraint-aware
test data.

## Solution

A Java library that accepts a JSON Schema (Draft 7) and produces valid JSON
data at runtime. It is designed specifically for test data generation:
constraints are respected, randomness is seeded for reproducibility, and
repeated generation is exhaustive — across repeated calls every possible value
is covered. For example: all enum values are produced, numeric boundary
conditions are hit, booleans take both true and false, and optional fields
appear both present and absent.

## Intended Users

Java developers who need to generate valid JSON fixtures in automated tests.

## Requirements

1. Accept a JSON Schema (Draft 7) and return valid JSON data.
2. Support seeded randomness so tests are reproducible.
3. Respect string constraints: `minLength`, `maxLength`, `pattern`.
4. Respect numeric constraints: `minimum`, `maximum`, `exclusiveMinimum`,
   `exclusiveMaximum`, `multipleOf`.
5. Across repeated calls, cover all `enum` values.
6. Across repeated calls, hit boundary values for integer constraints
   (min, max, min+1, max-1).
7. Always generate `required` fields. Optionally generate optional fields,
   covering both presence and absence across repeated calls.
8. Resolve `$ref`, including recursive schemas.
9. Handle `allOf`, `anyOf`, and `oneOf`.
10. Allow callers to pin specific field values, overriding generation for a
    given path.

## Tech Stack

- **Java 21** — minimum supported version (current LTS).
- **Minimal production dependencies** — to keep the library lightweight
  for consumers.

## Out of Scope

- JSON Schema drafts other than Draft 7.
- Source code generation (`.java` files from a schema).
- A command-line interface.
- `format` keyword validation (e.g. `email`, `date-time`).

## Further Notes

The exhaustiveness requirement distinguishes this library from a pure random
generator. The generation strategy is closer to boundary value analysis than
to fuzzing.
