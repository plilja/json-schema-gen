# $ref Resolution

## What to build

Implement resolution of `$ref` within a schema document, including
recursive schemas. A `$ref` points to a definition elsewhere in the
same document and the generator must follow it to produce a valid value.

## Acceptance criteria

- [x] `$ref` pointing to a definition in `definitions` / `$defs` is
      resolved and generation continues from the referenced schema
- [x] Recursive `$ref` (a schema that references itself) terminates
      gracefully without infinite recursion
- [x] Integration tests validate output for schemas using `$ref`
- [x] Integration test covers a recursive schema (e.g. a tree node)
- [x] `mvn test` passes

## Implementation notes

- `$ref` is a field on `Schema` base (cross-cutting, like `enum`); a present
  `$ref` short-circuits to the resolved target at generation time.
- The parser walks the raw `JsonNode` tree once to collect every `$ref`
  string, resolves each via `JsonNode.at()` (full internal JSON Pointer),
  and returns a `SchemaDocument` (root + `Map<String, Schema>` ref index).
  `"#"` is special-cased to point at the root `Schema` instance so phase
  state is shared between root and self-references.
- A `GeneratorContext` is threaded through the generator tree: it owns
  the random source, an `IdentityHashMap<Schema, JsonGenerator>` cache,
  and a `minimal` flag. The ref index dedupes by ref string — each
  distinct string is deserialised to one `Schema` instance — so N
  references via the same ref string share one `JsonGenerator` and one
  phase cycle. Two *different* ref strings pointing at the same target
  (e.g. aliases) would deserialise to separate `Schema` instances and
  miss the cache; in practice this is rare and not a correctness issue.
- Recursion depth is tracked per `RefGenerator` instance, not on the
  context. Since the `JsonGenerator` cache is identity-keyed and each
  target schema has its own `RefGenerator`, the bound is per target — so
  under mutual recursion (A↔B) the effective total nesting ceiling is
  `HARD_DEPTH × cycle length`, still bounded, just looser.
- Recursion cycles are broken at generation time, not construction time:
  `RefGenerator` only calls `context.generatorForRef(...)` inside
  `generatePhase`, and `GeneratorContext.generatorFor` returns the cached
  `JsonGenerator` for a target schema via `computeIfAbsent`. So building a
  `JsonGenerator` for a recursive schema never re-enters itself.
- Recursion termination uses two thresholds (soft 5, hard 10), marked with
  a TODO to make them configurable. At soft depth, the `RefGenerator`
  flips the context into minimal mode; downstream `ObjectGenerator` skips
  optional fields and `ArrayGenerator` collapses to `minItems` — so
  natural escape hatches (`children: []`, omitted optional refs) are taken
  early. At hard depth, `UnsatisfiableSchemaException` is thrown for
  schemas with required infinite recursion.
- Only internal pointer refs (`#`, `#/...`) are supported; external URI
  refs throw `IllegalArgumentException`.

## Blocked by

#0012 — Object type generation
