# Code conventions

## Code style

Google Java Style Guide (modified), enforced by Checkstyle
(`checkstyle:check` runs on `mvn verify`). Use 4-space indentation and a
160-character line length. Violations fail the build.

Use the `var` keyword whenever possible, except for primitives and their boxed
counterparts (`int`/`Integer`, `long`/`Long`, `double`/`Double`, etc.). Spelling
out the primitive type avoids silent boxing — e.g. `var x = coalesce(getInt(), 0)`
infers `Integer`, turning `x == y` into a reference comparison.

Avoid nesting multiple calls or operations on one line (e.g.
`generate(lookupGenerator(computeValue(a), b), b)`). Break each step out into a
named local variable instead — it makes the sequence of operations readable and
each intermediate value debuggable.

## Design conventions

Follows John Ousterhout's "A Philosophy of Software Design" approach.

- **Prefer deep methods with simple contracts over many shallow ones.** When the
  choice is between one longer, more complex method and spreading the same logic
  across several small methods plus extra helper classes/records/fields, prefer
  the single method. Complexity inside one well-named method is cheaper than
  complexity in the class structure and the contracts between the pieces — a
  reader follows one body top to bottom instead of hopping between fragments and
  holding their interfaces in their head. Split only when a piece has real reuse
  or a genuinely independent, simpler contract of its own, not merely to make
  each method short.

## Documentation conventions

Follows John Ousterhout's "A Philosophy of Software Design" approach.

- **Javadoc defines the abstraction.** A reader should be able to use a class or
  method from its javadoc alone, without reading the implementation. Include
  preconditions, postconditions, side effects, and what the thing *means* at a
  higher level than the signature conveys.
- **Never leak implementation details into javadoc.** Describe what a method does
  and means, not how it does it internally.
- **Design the abstractions before any of them.** "Javadoc before
  implementation" applies to the whole change, not one method at a time: before
  implementing, decide which classes/methods the change needs and sketch each
  one's contract — signature + javadoc — before writing any of their bodies.
  Deciding method B's contract while method A's implementation is already
  influencing your thinking defeats the purpose as much as writing one method's
  doc and body together does. This is what surfaces a wrong split (a method doing
  two things, a missing seam, a redundant one) while it's still cheap to change.
- **Write javadoc before the implementation.** This is a design tool — it forces
  the comment to describe the abstraction, not the implementation, and clarifies
  thinking before code is written. "Before" must be a real separation, not just
  javadoc appearing above the code in the same edit — drafting both together
  after the mechanism is already known (e.g. from tracing or TDD) reliably leaks
  implementation into the doc regardless of line order. Achieve this with
  **stub-then-fill**: one edit adds the signature, javadoc, and a placeholder
  body (e.g. `throw new UnsupportedOperationException("TODO")`); a second edit
  fills in the body only, without touching the javadoc. If the implementation
  ends up needing a different contract, revise the javadoc as its own visible
  step, not a quiet patch made while filling the body.
- **Leak self-check.** Before finalizing a javadoc block, re-read it — does it
  name a loop, a private field, an attempt counter, or verbs like "retries",
  "advances", "iterates", "starting from"? Any of those means it's describing
  *how*, not *what*; rewrite it to state preconditions/postconditions/behavior,
  and move the mechanism into an inline comment instead.
- **Prefer good names over docs.** A well-named method or parameter eliminates
  the need for javadoc. Rename `n` to `length` instead of adding
  `@param n the length`.
- **Don't document the obvious.** If the code already says it clearly, a comment
  is noise.
- **`@param`/`@return` tags** only when they add information beyond what good
  naming already conveys.
- **Implementation comments** only for non-obvious things — subtle reasoning, why
  a particular approach was chosen, tricky invariants.
- **Section comments** to give structure to long methods.
- **Cross-module hacks** — when forced into a dependency that can't be
  eliminated, document it at both ends so future readers know why.

Scope:

- `api` package: full javadoc on every public class and method.
- Internal code: javadoc on classes and non-trivial methods.

## Test conventions

Use `// when` and `// then` comments to separate test phases.
