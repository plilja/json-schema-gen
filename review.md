# Review — pending changes vs `HEAD`

Reviewed: 3 modified files + 7 new files (issue #0013 — Array Type Generation).

## Standards

**Hard violations**

1. **`ArrayGenerator.java:53` — raw type `ArrayList<>`**
   ```java
   var list = new ArrayList<>();
   ```
   Should be `new ArrayList<Object>()` to match the return type `List<Object>`. Sister `ObjectGenerator.java:33` uses explicit `new LinkedHashMap<String, Object>()`. Standard: consistency with sister class.

2. **`ArraySchema.java` — no default field values, breaks symmetry with `ObjectSchema`**
   `ObjectSchema` initialises `properties = Map.of()` and `required = List.of()` so the model is safe to use without explicit construction. `ArraySchema` leaves `items`, `minItems`, `maxItems` null, forcing every consumer (here `ArrayGenerator`) to null-check. Judgement call, inconsistent with sister-class convention.

**Judgement calls**

3. **`ArrayGenerator.java:36` — `RANDOM` phase can produce min/max again.** `min + random.nextInt(max - min + 1)` includes min and max in the random range. Not a violation but `ObjectGenerator`'s RANDOM excludes already-covered shapes.

4. **`array-simple.json`** — `{"type": "array"}` with no `items`. Generator handles it but produces literal-`null` element arrays; worth confirming the integration test asserts schema validity.

**Clean**

- Package layering (ADR-0003) clean: `ArrayGenerator` imports only `model.ArraySchema`; `ArraySchema` Jackson use is in `model`. OK.
- `var` usage (CLAUDE.md) consistent throughout.
- Sealed hierarchy update in `Schema.java` correctly adds `ArraySchema` to `permits`. OK.
- Issue-file checkbox flips match the workflow rule.

## Spec

**(a) Missing or partially implemented**

- **"Boundary values for array length are covered across repeated calls"** (line 13) — Partial. `PhaseGenerator` cycles `MIN_LENGTH → MAX_LENGTH → RANDOM` so boundaries are emitted. But when only `minItems` is set (no `maxItems`), MAX_LENGTH is fabricated from a magic constant `DEFAULT_MAX_LENGTH = 5` — that is not a real schema boundary. Sister `NumericGenerator` `skip()`s the MAX phase when there is no upper bound (`NumericGenerator.java:37`); `ArrayGenerator` should mirror that at `ArrayGenerator.java:32-37,45-50`.
- **"Integration tests validate output against the schema"** (line 14) — Mostly satisfied. But no schema covers the "only `minItems`, no `maxItems`" case, exactly where the fabricated default-max boundary applies. `array-of-strings.json` overlaps with `array-bounded.json`.
- **"`mvn test` passes"** (line 16) — Box is checked, but `mvn test` actually FAILS on `IntegrationTest.generatesValidJson:[integer-multiple-of.json invocation=7]` (preexisting numeric bug, not introduced here). Box should not be ticked while build is red.

**(b) Scope creep** — None. `ArraySchema` only carries the three keywords the spec asked for. No `uniqueItems`, `contains`, or tuple-form `items` (good).

**(c) Looks implemented but wrong/incomplete**

- **"`minItems` and `maxItems` constraints are respected"** (line 11) — When neither is set, `min=0`, `max=5` (the magic default). When only `minItems` is set, `max = max(min, 5)`. RANDOM phase silently caps length at an arbitrary `min+5` even though the schema imposes no upper bound — surprising and undocumented.
- **Element generation for items-less schemas**: `itemGenerator != null ? ... : null` (`ArrayGenerator.java:55`) emits literal `null` elements for `{"type":"array"}`. Valid per Draft 7 but thin.
- **Test gaps** (`ArrayGeneratorTest.java`): no assertion that the MIN boundary is hit; `boundaryLengthsAreCoveredAcrossRepeatedCalls` only checks 2 and 5 appear, not that they appear deterministically in the first two calls.

---

**Summary** — Standards: 2 hard violations + 2 judgement calls. Spec: 2 partial criteria, 0 scope creep, 2 implementation concerns. **Worst issue:** the `mvn test` acceptance criterion is checked while `mvn test` is red (preexisting failure, but the checkbox is misleading).
