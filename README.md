# Gjuton

Gjuton is a test-data generator for Java. Point it at a JSON Schema and it
produces JSON that is valid against that schema. It offers two modes of
operation: *random* (the default), which emits arbitrary valid values, and
*exhaustive*, which deliberately exercises the schema's boundary conditions to
surface bugs.

Gjuton is pre-1.0 — the API is unstable and breaking changes may land in any release.

## Requirements

- Java 21 or later

## Installation

Maven:

```xml
<dependency>
    <groupId>io.github.gjuton</groupId>
    <artifactId>gjuton</artifactId>
    <version>0.0.1</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.gjuton:gjuton:0.0.1'
```

Gjuton is pre-1.0 — breaking changes may land in any release. Pin a specific
version and check the [changelog](CHANGELOG.md) before upgrading.

Gjuton pulls in two transitive dependencies: Jackson (JSON serialization) and
RgxGen (`pattern` generation).

## Quick start

Create a generator from a schema and call `generate()`. The schema goes in as a
`String`, `File`, or `InputStream`; a JSON value comes out as a `String`.

```java
import io.github.gjuton.api.Gjuton;

String schema = """
        {
          "type": "object",
          "properties": {
            "id":    { "type": "integer", "minimum": 1 },
            "name":  { "type": "string", "minLength": 1 },
            "email": { "type": "string", "format": "email" }
          },
          "required": ["id", "name"]
        }
        """;

String json = Gjuton.of(schema).generate();
```

Each call to `generate()` returns another valid value for the same schema. By
default the values are random; switch to `EXHAUSTIVE` mode (see below) to emit
trouble-prone boundary values (empty strings, minimums, maximums, zero) first,
to expose bugs in the system under test.

## Configuration

A `Gjuton` instance is configured through `with*` methods. Each returns a new,
independently configured instance and leaves the original unchanged, so calls
chain naturally.

```java
Gjuton gen = Gjuton.of(schema)
        .withSeed(42)
        .withGenerationMode(GenerationMode.EXHAUSTIVE);
```

### Reproducible output

`withSeed(long)` fixes the random source. Two generators with the same schema
and seed produce the same sequence of values across repeated `generate()` calls
— so a failing test can be replayed with identical data.

```java
Gjuton gen = Gjuton.of(schema).withSeed(42);
```

### Generation mode

`withGenerationMode(GenerationMode)` selects how values are chosen:

- `RANDOM` (default) — emit only random valid values, skipping the boundary-value
  cycle. Faster and less repetitive when boundary coverage is not needed.
- `EXHAUSTIVE` — emit trouble-prone boundary values first, then random valid
  values. Maximises the chance of surfacing bugs.

```java
Gjuton gen = Gjuton.of(schema).withGenerationMode(GenerationMode.EXHAUSTIVE);
```

### Value overrides

`withOverrideByPath(String jsonPath, ValueOverride)` overrides the value at a
specific path with whatever your override returns, instead of generating it from
the schema. Use it for application-level constraints the schema can't express — a
user id that must exist in your test database, say — or to plug in a data-faker
library.

Paths match exactly (no wildcards): `$` is the root, `$.a.b` a nested field,
`$.items[0]` an array element.

```java
Gjuton gen = Gjuton.of(schema)
        // a fixed value: pin a user id that exists in the test database
        .withOverrideByPath("$.userId", () -> 71)
        // a fresh value each call, composed with a data faker
        .withOverrideByPath("$.email", faker.internet()::emailAddress);
```

`withOverrideByName(String propertyName, ValueOverride)` does the same thing but
matches by property name instead of path — it fires at every position where that
name appears, without enumerating every path. All positions with the same name
share one value per `generate()` call. When both a path-based and a name-based
override match the same position, the path-based override wins.

```java
Gjuton gen = Gjuton.of(schema)
        .withOverrideByName("customerId", () -> "cust-42");
```

### Value constraints

`withConstraints(Constraints)` narrows generated values beyond what the schema
requires, so you can pin fixtures to a realistic or bounded shape without editing
the schema. Build a `Constraints` from `Constraints.of()` and set only the kinds
you care about; every kind left unset keeps its schema-driven behavior.

Each bound only ever tightens: at every position the effective range is the
intersection of the schema's own constraint and the matching bound, so a bound
looser than the schema has no effect and one stricter replaces it. A position
whose intersection admits no value fails generation with
`UnsatisfiableSchemaException`.

```java
Gjuton gen = Gjuton.of(schema).withConstraints(Constraints.of()
        .stringLength(1, 40)                                       // string length
        .numberRange(0, 1000)                                      // integers and numbers
        .dateRange(Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")) // date / date-time
        .alphabet("abcdefghijklmnopqrstuvwxyz")                    // characters strings may use
        .arrayLength(0, 5));                                       // array length
```

Strings with a recognized `format` (such as `email` or `uri`) are produced by
that format's generator, which owns their shape — `stringLength` and `alphabet`
do not apply to them. `alphabet` is also ignored for strings whose schema
carries a `pattern` (the pattern governs those).

### Additional properties

`withAdditionalProperties()` adds random extra properties to generated objects
wherever the schema permits them (`additionalProperties` absent or `true`), to
exercise consumers that don't expect unknown fields. Off by default.

```java
Gjuton gen = Gjuton.of(schema).withAdditionalProperties();
```

### Recursion limits

For schemas with recursive `$ref` chains, you can tune the depth at which
recursion collapses to the smallest valid form:

- `withRecursionLimitsShallow()` — favour compact output.
- `withRecursionLimitsDeep()` — for schemas with legitimately deep nesting.
- `withRecursionLimits(int soft, int hard)` — set the ceilings explicitly. At the
  soft ceiling recursive structures collapse to their smallest valid form; at the
  hard ceiling a `$ref` that still hasn't bottomed out is treated as
  unsatisfiable and generation fails.

```java
Gjuton gen = Gjuton.of(schema).withRecursionLimitsShallow();
```

## Knowing when to stop

`noveltyScore()` reports the fraction of recent `generate()` calls that produced
at least one value not already seen, in `[0, 1]`. It starts at `1.0` before any
call and trends toward `0.0` as the generator exhausts its repertoire. Works in
both `RANDOM` and `EXHAUSTIVE` mode.

```java
Gjuton gen = Gjuton.of(schema).withGenerationMode(GenerationMode.EXHAUSTIVE);
List<String> samples = new ArrayList<>();
while (gen.noveltyScore() > 0.0) {
    samples.add(gen.generate());
}
```

## Behavior notes

- **Unsupported keywords are ignored.** If a schema uses a keyword Gjuton doesn't
  implement, it generates data as if the keyword weren't there. This keeps
  real-world schemas working rather than failing on an unimplemented feature; the
  result may be more permissive than the schema, never invalid.
- **Contradictory schemas fail fast.** When constraints are mutually exclusive
  (e.g. `minimum: 10, maximum: 5`), `generate()` throws
  `UnsatisfiableSchemaException` — the schema itself is the bug, and it surfaces
  immediately.
- **Instances are not thread-safe.** Each thread should use its own generator.
- **`pattern` strings are generated with RgxGen.** String values constrained by a
  `pattern` regular expression are produced using the RgxGen library. If RgxGen
  cannot produce a string matching a given pattern, override that path with a
  custom override that supplies a matching string yourself.
