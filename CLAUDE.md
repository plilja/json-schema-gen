# json-schema-gen

Runtime test data generator for Java. Takes a JSON Schema (Draft 7) and
produces valid JSON. Designed for use in automated tests where hand-crafted
fixtures are error-prone and incomplete.

## Build commands

```
mvn clean verify        # full build + tests
mvn clean compile       # compile only
mvn test                # run unit tests
```

## Architecture

Three-phase pipeline:

1. **Parser** — deserializes a JSON Schema document into the internal
   schema model using Jackson data-binding annotations.
2. **Model** — Java classes representing schema constructs
   (type, constraints, $ref, combining keywords, etc.).  Model classes
   carry Jackson annotations for deserialization; the parser is a thin
   wrapper around `ObjectMapper.readValue()`.
3. **Generator** — walks the model and produces a JSON value string.

Public API accepts a schema as a `String`, `File`, or `InputStream`.
It returns a plain `String`. No third-party types are exposed.
Convenience overloads write to `OutputStream`, `Writer`, or `File`.

## Schema model design

Cross-cutting JSON Schema keywords (`enum`, `const`, `if`/`then`/`else`,
combining keywords) are fields on the `Schema` base class, not separate
schema types. Type-specific properties (e.g. `minimum` for integers) live
on concrete subclasses. The generator checks cross-cutting keywords before
type-based dispatch.

## Generation strategy

The generator prioritises values that are likely to expose bugs in the
system under test. For each type, deterministic "trouble-prone" values
are emitted first (e.g. empty string for strings; min, max, zero for
integers), followed by random valid values. This is what "boundary-value
exhaustiveness" means in issue acceptance criteria.

## Package conventions

```
se.plilja.jsonschemagen
├── api          public API — everything a consumer imports
└── internal     implementation detail, not part of the public contract
    ├── parser   JSON → schema model
    ├── model    schema model classes
    └── generator model → JSON value string
```

Allowed dependencies (enforced by ArchUnit — violations fail `mvn test`):

```
api          — entry point; may access all layers
parser       — may only access model
generator    — may only access model
model        — leaf; no dependencies on other internal packages
```

Jackson (`com.fasterxml.jackson`) is allowed only in `parser` and `model`.
Consumers must only import from `api`, never from `internal` directly.

## Testing

Integration tests are parameterized and driven by schema files in
`src/test/resources/schemas/`. Adding a schema file is all that's needed to add
a test case.

## Issues

Issues live in `docs/issues/`. After completing work on an issue, update the
issue file: check off acceptance criteria and revise any descriptions that
no longer match the implementation.

## Code style

Google Java Style Guide (modified), enforced by Checkstyle (`checkstyle:check` runs on `mvn verify`).
Use 4-space indentation, 160-char line length. Violations fail the build.
Use `var` keyword whenever possible.

## Test conventions

Use `// when` and `// then` comments to separate test phases.

## Workflow

- Never commit unless explicitly told to commit.
- Never start implementing unless explicitly told to start.
