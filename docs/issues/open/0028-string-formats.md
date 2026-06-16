# String `format` support

## What to build

JSON Schema's `format` keyword on a string schema declares that the
value must conform to a named lexical format. Draft 7 lists a number of
formats; the high-traffic ones in real schemas are:

- `date`, `date-time`, `time` (RFC 3339)
- `email`, `idn-email` (RFC 5321 / RFC 6531)
- `uri`, `uri-reference`, `iri`, `iri-reference` (RFC 3986 / 3987)
- `hostname`, `idn-hostname` (RFC 1034 / 5890)
- `ipv4`, `ipv6` (RFC 2673 / 4291)
- `uuid` (RFC 4122)
- `regex` (ECMA-262)
- `json-pointer`, `relative-json-pointer` (RFC 6901)

Today the generator ignores `format` entirely — it produces a random
string that satisfies length constraints but rarely satisfies the
declared format, so validators downstream reject the generated value.

This issue introduces per-format generators that produce values
conforming to each named format. As with the rest of the generator,
"boundary-value exhaustiveness" applies: emit a small set of canonical
representative values (the minimum legal value, a typical value, a
maximum-ish or unusual-but-legal value) before falling back to random
conforming values.

## Status

`email`, `idn-email`, `uuid`, `date`, `date-time`, `time`, `hostname`, `idn-hostname`, `ipv4`, `ipv6`, and `uri` are implemented; remaining formats track here.

## Acceptance criteria

- [ ] Each format in the list above generates values that validate
      against a strict Draft 7 validator
      - [x] `email`
      - [x] `date`, `date-time`, `time`
      - [x] `idn-email`
      - [x] `uri`
      - [ ] `uri-reference`, `iri`, `iri-reference`
      - [x] `hostname`
      - [x] `idn-hostname`
      - [x] `ipv4`, `ipv6`
      - [x] `uuid`
      - [ ] `regex`
      - [ ] `json-pointer`, `relative-json-pointer`
- [x] An unknown / unrecognised `format` is treated as a no-op (string
      is generated according to the other constraints) — `format` is
      annotation-only by default in Draft 7
- [x] `pattern` and `format` on the same string compose — generated
      values satisfy both (when satisfiable) — implemented for `email`
- [x] Length constraints (`minLength`, `maxLength`) compose with
      `format` where the format permits — implemented for `email`,
      `idn-email`, `hostname`, `idn-hostname`, `ipv4`, `ipv6`, `uri`
      (length-bounded formats throw an `UnsatisfiableSchemaException`
      up-front when bounds exclude the format's reachable length range)
- [x] `mvn verify` passes

## Blocked by

#0005 — String type generation
#0010 — String constraints
