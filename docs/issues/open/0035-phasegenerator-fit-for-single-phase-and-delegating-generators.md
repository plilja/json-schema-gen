# `PhaseGenerator` fit for single-phase and delegating generators

## What to build

`PhaseGenerator<E, R>` models a "boundary-values first, then random"
progression: declare an enum of phases, advance through them, return
`Skip` for phases that don't apply, and the framework loops until one
returns `Present`. That shape is a good fit for `NumericGenerator`,
`StringGenerator`, and the format generators. It's an awkward fit for
two other groups:

**Single-phase generators** — `NullGenerator`, `AllOfGenerator`,
`RefGenerator` each declare a one-element enum (`NULL`, `ONLY_PHASE`,
`REF`) just to satisfy the type parameter. They have no
boundary-then-random cycle. The framework's loop, `minimalPhase()`, and
`advanceToNext()` machinery is dead weight for them.

**Pure delegators** — `AllOfGenerator` and `RefGenerator` don't own a
phase progression at all: every call forwards to a delegate
(`merged.generate()` / `target.generate()`) and the delegate is what
cycles through boundary values. Putting an enum on the outer class
implies it owns a phase cycle, but it doesn't.

**`OneOf`/`AnyOf` minimal-mode bug** — these *do* have meaningful phases
(exhaustive sub-schema cycling, then random), so they should stay on
`PhaseGenerator`. But `generatePhase(EXHAUSTIVE)` reads
`subSchemas.get(index)`, and in minimal mode `index` is whatever
non-minimal traffic left it at. "Minimal" should pin to the first
sub-schema, not the current one.

The intended cleanup:

- Introduce a small `Generator<R>` interface exposing `generate()`.
- `PhaseGenerator` implements `Generator`. Multi-phase generators stay
  as-is.
- `NullGenerator`, `AllOfGenerator`, `RefGenerator` implement
  `Generator` directly and drop their dummy enum.
- `JsonGenerator.delegate` becomes `Generator<?>` rather than
  `PhaseGenerator<?, ?>`.
- `OneOfGenerator` / `AnyOfGenerator` reset their exhaustive index (or
  bypass it) when called in minimal mode so the smallest branch is
  picked.

No change to public API or to generation output for the non-minimal
path. Minimal-mode output for `oneOf` / `anyOf` may change (now pins to
branch 0).

## Acceptance criteria

- [ ] A `Generator<R>` interface exists; `PhaseGenerator` implements it
- [ ] `NullGenerator`, `AllOfGenerator`, `RefGenerator` no longer extend
      `PhaseGenerator` and have no `GenerationPhase` enum
- [ ] `JsonGenerator` holds and dispatches through `Generator<?>`
- [ ] `OneOfGenerator` / `AnyOfGenerator` in minimal mode pick the first
      sub-schema regardless of prior index state
- [ ] Existing tests pass unchanged for non-minimal generation
- [ ] `mvn verify` passes
