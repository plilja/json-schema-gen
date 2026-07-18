# Gjuton

Gjuton is a test-data generator for Java. Point it at a JSON Schema and it
produces JSON that is valid against that schema. It offers two modes of
operation: *exhaustive*, which deliberately exercises the schema's boundary
conditions to surface bugs, and *random*, which emits arbitrary valid values.

## Requirements

- Java 21 or later

## Installation

Maven:

```xml
<dependency>
    <groupId>io.github.gjuton</groupId>
    <artifactId>gjuton</artifactId>
    <!-- TODO: version — not yet published to Maven Central -->
</dependency>
```

Gradle:

```groovy
// TODO: version — not yet published to Maven Central
implementation 'io.github.gjuton:gjuton'
```

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

Each call to `generate()` returns another valid value for the same schema.
Trouble-prone boundary values (empty strings, minimums, maximums, zero) are
emitted first to expose bugs in the system under test, followed by random valid
values.

## Configuration

A `Gjuton` instance is configured through `with*` methods. Each returns a new,
independently configured instance and leaves the original unchanged, so calls
chain naturally.

```java
Gjuton gen = Gjuton.of(schema)
        .withSeed(42)
        .withGenerationMode(GenerationMode.RANDOM_ONLY);
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

- `EXHAUSTIVE` (default) — emit trouble-prone boundary values first, then random
  valid values. Maximises the chance of surfacing bugs.
- `RANDOM_ONLY` — emit only random valid values, skipping the boundary-value
  cycle. Faster and less repetitive when boundary coverage is not needed.

```java
Gjuton gen = Gjuton.of(schema).withGenerationMode(GenerationMode.RANDOM_ONLY);
```

### Custom value producers

`withProducer(String jsonPath, ValueProducer)` overrides the value at a specific
path with whatever your producer returns, instead of generating it from the
schema. Use it for application-level constraints the schema can't express — a
user id that must exist in your test database, say — or to plug in a data-faker
library.

Paths match exactly (no wildcards): `$` is the root, `$.a.b` a nested field,
`$.items[0]` an array element.

```java
Gjuton gen = Gjuton.of(schema)
        // a fixed value: pin a user id that exists in the test database
        .withProducer("$.userId", () -> 71)
        // a fresh value each call, composed with a data faker
        .withProducer("$.email", faker.internet()::emailAddress);
```

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

Under the default `EXHAUSTIVE` mode, `valueCoverage()` reports how thoroughly
`generate()` has exercised the schema so far, as a fraction in `[0, 1]` of its
deliberate value set — every enum literal, each boundary value, both booleans,
and each const value. The fraction never decreases and reaches `1.0` only once
every deliberate value has been emitted, so you can generate towards a target and
stop:

```java
Gjuton gen = Gjuton.of(schema);
List<String> samples = new ArrayList<>();
while (gen.valueCoverage() < 0.95) {
    samples.add(gen.generate());
}
```

Under `RANDOM_ONLY` there is no boundary-value cycle, so coverage does not climb
to a high target.

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
  custom producer that supplies a matching string yourself.
