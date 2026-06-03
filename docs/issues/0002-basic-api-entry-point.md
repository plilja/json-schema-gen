# Basic API Entry Point

## What to build

Define the public API surface for the generator. Callers should be able to
construct a generator from a JSON schema string and invoke it to get a JSON
string back. The API should support an optional seed for reproducible output.
The exact API shape is provisional and will be refined in a follow-up task.

## Acceptance criteria

- [ ] A public class in `se.plilja.jsonschemagen.api` accepts a JSON schema
      string and produces a JSON string
- [ ] An optional seed can be provided to make output reproducible — two
      calls with the same schema and seed produce identical output
- [ ] The public API does not expose any internal or third-party types
- [ ] The module still compiles and `mvn clean verify` passes

## Blocked by

#0001 — Project scaffold
