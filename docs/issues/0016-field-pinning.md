# Field Pinning

## What to build

Allow callers to pin specific field values in the generated output,
overriding generation for a given path. The rest of the object is
still generated from the schema.

## Acceptance criteria

- [ ] Caller can specify a JSON path and a fixed value; that value
      appears in the output unchanged
- [ ] Unpinned fields are still generated from the schema
- [ ] Pinned values are validated against the schema at the pinned path
      (or documented clearly if validation is out of scope)
- [ ] Integration tests cover pinning a top-level field and a nested field
- [ ] `mvn test` passes

## Blocked by

#0012 — Object type generation
