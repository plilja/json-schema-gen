# Integration Test Baseline

## What to build

Set up the integration test infrastructure that all high-level tests will
use. Tests generate a JSON string from a schema and validate it against
that same schema using an external validator library.

## Acceptance criteria

- [ ] `networknt/json-schema-validator` added as a test-scoped dependency
- [ ] A reusable test helper exists that takes a schema string and a
      generated JSON string and asserts the output is valid according to
      the schema
- [ ] At least one smoke test exercises the helper end-to-end
- [ ] `mvn test` passes

## Blocked by

#0002 — Basic API entry point
