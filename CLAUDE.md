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

1. **Parser** — reads a JSON Schema document (Jackson tree) and produces
   an internal schema model.
2. **Model** — plain Java classes representing schema constructs
   (type, constraints, $ref, combining keywords, etc.).
3. **Generator** — walks the model and produces a JSON value string.

Public API accepts a schema as a `String`, `File`, or `InputStream`.
It returns a plain `String`. No third-party types are exposed.
Convenience overloads write to `OutputStream`, `Writer`, or `File`.

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
