# json-schema-gen

Runtime test data generator for Java. Takes a JSON Schema (Draft 7) and
produces valid JSON. Designed for use in automated tests where hand-crafted
fixtures are error-prone and incomplete.

## Build commands

```
mvnd clean verify -Pfast # inner-loop: skips checkstyle + spotbugs (~5s warm)
mvn clean verify         # full build incl. style/static analysis (~14s) — pre-commit gate
mvn clean compile        # compile only
mvn test                 # run unit tests
```

Use `mvnd verify -Pfast` during work to keep the iteration loop tight.
Run plain `mvn clean verify` once before declaring a task done so style
and static-analysis gates are checked.

### mvnd (Maven Daemon)

`mvnd` keeps a JVM warm between Maven invocations, cutting warm-run
wall-clock by ~40–50% vs. plain `mvn`. Install it once per machine; no
per-repo setup needed.

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
├── errors       Exception types (exposed public, thrown from internal)
└── internal     implementation detail, not part of the public contract
    ├── parser   JSON → schema model
    ├── model    schema model classes
    └── generator model → JSON value string
```

Allowed dependencies (enforced by ArchUnit — violations fail `mvn test`):

```
api          — entry point; may access all layers
parser       — may only access model and errors
generator    — may only access model and errors
model        — leaf; no dependencies on other internal packages
errors       — leaf; no dependencies on other packages
```

Jackson (`com.fasterxml.jackson`) is allowed only in `parser` and `model`.
Consumers must only import from `api`, never from `internal` directly.

## Testing

Integration tests are parameterized and driven by schema files in
`src/test/resources/schemas/`. Adding a schema file is all that's needed to add
a test case.

## Issues

Issues live in `docs/issues/open/` and `docs/issues/closed/`. After
completing work on an issue, check off acceptance criteria, revise any
descriptions that no longer match the implementation, and `git mv` the
file from `open/` into `closed/`.

Issue numbers are globally unique across both folders — the next new
issue gets the next free number regardless of which folder it lands in.

## Code style

Google Java Style Guide (modified), enforced by Checkstyle (`checkstyle:check` runs on `mvn verify`).
Use 4-space indentation, 160-char line length. Violations fail the build.
Use the `var` keyword whenever possible, except for primitives and
their boxed counterparts (`int`/`Integer`, `long`/`Long`,
`double`/`Double`, etc.). Spelling out the primitive type avoids
silent boxing — e.g. `var x = coalesce(getInt(), 0)` infers `Integer`,
turning `x == y` into reference comparison.

## Test conventions

Use `// when` and `// then` comments to separate test phases.

## Workflow

- Never commit unless explicitly told to commit.
- Never start implementing unless explicitly told to start. The user
  answering scope, design, or clarifying questions is NOT a green light —
  it refines the plan, nothing more. Wait for an explicit imperative
  ("go", "start", "implement it", "do it", "proceed") before any
  state-changing tool use: `TaskCreate`, `Edit`, `Write`, or `Bash` that
  mutates the working tree. `Read`, `Grep`, and other read-only
  exploration are fine while planning. If you're unsure whether you have
  the green light, you don't — present the plan, ask "ready to start?",
  and wait.
- Do not introduce new patterns or invent new abstractions (factories,
  builders, strategy/visitor shapes, helper layers, new base classes,
  new interfaces, generic wrappers, etc.) unless the ticket explicitly
  calls for it OR you have asked and gotten explicit confirmation.
  Solving the stated problem with the existing shapes in the codebase
  is the default. "It would be cleaner" or "it would scale better" is
  not sufficient justification — propose it, wait for a yes, then
  implement.
