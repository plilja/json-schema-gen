# $ref Resolution

## What to build

Implement resolution of `$ref` within a schema document, including
recursive schemas. A `$ref` points to a definition elsewhere in the
same document and the generator must follow it to produce a valid value.

## Acceptance criteria

- [ ] `$ref` pointing to a definition in `definitions` / `$defs` is
      resolved and generation continues from the referenced schema
- [ ] Recursive `$ref` (a schema that references itself) terminates
      gracefully without infinite recursion
- [ ] Integration tests validate output for schemas using `$ref`
- [ ] Integration test covers a recursive schema (e.g. a tree node)
- [ ] `mvn test` passes

## Blocked by

#0012 — Object type generation
