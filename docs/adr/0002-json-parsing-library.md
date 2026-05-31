# ADR-0002: Internal JSON Parsing Library

## Status
Accepted

## Context
The library needs to parse the input JSON schema document and serialize
the generated output. A JSON library is required internally. Because the
output format is a plain string (see ADR-0001), this library is purely
an implementation detail and is not exposed in the public API.

## Decision
Use Jackson (`jackson-databind`) as the internal JSON library.

## Rationale
Jackson is the most widely used JSON library in the Java ecosystem.
Developers working with it are familiar with its API, and it is likely
already present on the classpath of most consumers.

## Alternatives Considered
- **Gson** — lighter and simpler API, but less widely used and less
  familiar to most Java developers.
- **org.json** — minimal footprint but a poor tree API that is
  unsuitable for walking a complex schema document.

## Consequences
- Jackson is a transitive production dependency for consumers.
- A future multi-module split could introduce alternative parser
  backends (e.g. a Gson module), making Jackson the default rather
  than the only option.
