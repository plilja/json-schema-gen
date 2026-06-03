# Object Type Generation with Required and Optional Fields

## What to build

Implement generation for `{"type": "object"}` schemas. Required fields
must always be present. Optional fields must appear both present and
absent across repeated calls.

## Acceptance criteria

- [ ] `{"type": "object"}` with `properties` generates a valid JSON object
- [ ] All fields listed in `required` are always present
- [ ] Optional fields (present in `properties` but not `required`) appear
      both present and absent across repeated calls
- [ ] Nested object schemas are handled recursively
- [ ] Integration tests validate output against the schema
- [ ] Unit tests cover required/optional field behaviour in isolation
- [ ] `mvn test` passes

## Blocked by

#0005 — String type generation
