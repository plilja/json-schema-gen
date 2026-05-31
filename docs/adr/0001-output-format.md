# ADR-0001: Output Format

## Status
Accepted

## Context
The generator produces a JSON value from a schema. That value needs to be
returned to the caller in some Java type. The choice affects what the
caller can do with the result and what dependencies the library exposes.

## Decision
Return a JSON string as the core output. Convenience overloads that write
to standard Java types (`OutputStream`, `Writer`, `File`) should be
available with no additional generation logic.

## Rationale
Returning a string keeps the public API completely free of third-party
library dependencies. Whether the library uses Jackson or Gson internally
is transparent to the caller. Callers can deserialize the string into a
typed object using whatever library they already use, which is
straightforward and familiar.

## Alternatives Considered
- **Jackson `JsonNode`** — convenient for Jackson users but ties the
  public API to Jackson as a required consumer dependency.
- **`Map<String, Object>`** — no external dependency but untyped and
  awkward to work with for nested structures.
- **Custom tree type** — full control and no external dependency, but
  adds significant API surface for consumers to learn.

## Consequences
- Callers are responsible for deserializing the output into their own
  types. This is a small, familiar step in practice.
- The internal JSON library (currently Jackson) can be swapped without
  any change to the public API.
