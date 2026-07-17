# json-schema-gen

Runtime test data generator for Java. Takes a JSON Schema and produces
valid JSON. Supports the most commonly used JSON Schema features across
drafts, rather than targeting full compliance with any single draft.
Designed for use in automated tests where hand-crafted fixtures are
error-prone and incomplete.

## Build commands

```
mvnd clean verify -Pfast # inner-loop: skips checkstyle + spotbugs (~5s warm)
mvn clean verify         # full build incl. style/static analysis (~14s) — pre-commit gate
mvn clean compile        # compile only
mvn test                 # run unit tests
```

Use `mvnd verify -Pfast` during work to keep the iteration loop tight.
Run plain `mvn clean verify` once before declaring a task done so style
and static-analysis gates are checked.

### mvnd (Maven Daemon)

`mvnd` keeps a JVM warm between Maven invocations, cutting warm-run
wall-clock by ~40–50% vs. plain `mvn`. Install it once per machine; no
per-repo setup needed.

## Architecture

Three-phase pipeline:

1. **Parser** — deserializes a JSON Schema document into the internal
   schema model using Jackson data-binding annotations.
2. **Model** — Java classes representing schema constructs
   (type, constraints, $ref, combining keywords, etc.).  Model classes
   carry Jackson annotations for deserialization; the parser is a thin
   wrapper around `ObjectMapper.readValue()`.
3. **Generator** — walks the model and produces a JSON value string.

Public API accepts a schema as a `String`, `File`, or `InputStream`.
It returns a plain `String`. No third-party types are exposed.
Convenience overloads write to `OutputStream`, `Writer`, or `File`.

## Schema model design

Cross-cutting JSON Schema keywords (`enum`, `const`, `if`/`then`/`else`,
combining keywords) are fields on the `Schema` base class, not separate
schema types. Type-specific properties (e.g. `minimum` for integers) live
on concrete subclasses. The generator checks cross-cutting keywords before
type-based dispatch.

## Generation strategy

The generator prioritises values that are likely to expose bugs in the
system under test. For each type, deterministic "trouble-prone" values
are emitted first (e.g. empty string for strings; min, max, zero for
integers), followed by random valid values. This is what "boundary-value
exhaustiveness" means in issue acceptance criteria.

## Package conventions

```
se.plilja.jsonschemagen
├── api          public API — everything a consumer imports
├── errors       Exception types (exposed public, thrown from internal)
└── internal     implementation detail, not part of the public contract
    ├── parser   JSON → schema model
    ├── model    schema model classes
    ├── generator model → JSON value string
    └── util     general-purpose utilities (math, random, string, functional)
```

Allowed dependencies (enforced by ArchUnit — violations fail `mvn test`):

```
api          — entry point; may access all layers
parser       — may only access model and errors
generator    — may only access model, errors, and util
model        — leaf; no dependencies on other internal packages
util         — leaf; no dependencies on other internal packages
errors       — leaf; no dependencies on other packages
```

Jackson (`com.fasterxml.jackson`) is allowed only in `parser` and `model`.
Consumers must only import from `api`, never from `internal` directly.

## Testing

Integration tests are parameterized and driven by schema files in
`src/test/resources/schemas/`. Adding a schema file is all that's needed to add
a test case.

## Issues

Issues are tracked on GitHub. After completing work on an issue, close
it on GitHub, checking off acceptance criteria and revising any
descriptions that no longer match the implementation.

## Code style

Google Java Style Guide (modified), enforced by Checkstyle (`checkstyle:check` runs on `mvn verify`).
Use 4-space indentation, 160-char line length. Violations fail the build.
Use the `var` keyword whenever possible, except for primitives and
their boxed counterparts (`int`/`Integer`, `long`/`Long`,
`double`/`Double`, etc.). Spelling out the primitive type avoids
silent boxing — e.g. `var x = coalesce(getInt(), 0)` infers `Integer`,
turning `x == y` into reference comparison.
Avoid nesting multiple calls/operations on one line (e.g.
`generate(lookupGenerator(computeValue(a), b), b)`). Break each step out
into a named local variable instead — it makes the sequence of
operations readable and each intermediate value debuggable.

## Design conventions

Follows John Ousterhout's "A Philosophy of Software Design" approach.

- **Prefer deep methods with simple contracts over many shallow ones.**
  When the choice is between one longer, more complex method and
  spreading the same logic across several small methods plus extra
  helper classes/records/fields, prefer the single method. Complexity
  inside one well-named method is cheaper than complexity in the class
  structure and the contracts between the pieces — a reader follows one
  body top to bottom instead of hopping between fragments and holding
  their interfaces in their head. Split only when a piece has real
  reuse or a genuinely independent, simpler contract of its own, not
  merely to make each method short.

## Documentation conventions

Follows John Ousterhout's "A Philosophy of Software Design" approach.

- **Javadoc defines the abstraction.** A reader should be able to use a
  class or method from its javadoc alone, without reading the
  implementation. Include preconditions, postconditions, side effects,
  and what the thing *means* at a higher level than the signature conveys.
- **Never leak implementation details into javadoc.** Describe what a
  method does and means, not how it does it internally.
- **Design the abstractions before any of them.** "Javadoc before
  implementation" applies to the whole change, not one method at a
  time: before implementing, decide which classes/methods the change
  needs and sketch each one's contract — signature + javadoc — before
  writing any of their bodies. Deciding method B's contract while
  method A's implementation is already influencing your thinking
  defeats the purpose as much as writing one method's doc and body
  together does. This is what surfaces a wrong split (a method doing
  two things, a missing seam, a redundant one) while it's still cheap
  to change.
- **Write javadoc before the implementation.** This is a design tool —
  it forces the comment to describe the abstraction, not the
  implementation, and clarifies thinking before code is written.
  "Before" must be a real separation, not just javadoc appearing above
  the code in the same edit — drafting both together after the
  mechanism is already known (e.g. from tracing or TDD) reliably leaks
  implementation into the doc regardless of line order. Achieve this
  with **stub-then-fill**: one edit adds the signature, javadoc, and a
  placeholder body (e.g. `throw new UnsupportedOperationException("TODO")`);
  a second edit fills in the body only, without touching the javadoc.
  If the implementation ends up needing a different contract, revise
  the javadoc as its own visible step, not a quiet patch made while
  filling the body.
- **Leak self-check.** Before finalizing a javadoc block, re-read it —
  does it name a loop, a private field, an attempt counter, or verbs
  like "retries", "advances", "iterates", "starting from"? Any of those
  means it's describing *how*, not *what*; rewrite it to state
  preconditions/postconditions/behavior, and move the mechanism into an
  inline comment instead.
- **Prefer good names over docs.** A well-named method or parameter
  eliminates the need for javadoc. Rename `n` to `length` instead of
  adding `@param n the length`.
- **Don't document the obvious.** If the code already says it clearly,
  a comment is noise.
- **`@param`/`@return` tags** only when they add information beyond
  what good naming already conveys.
- **Implementation comments** only for non-obvious things — subtle
  reasoning, why a particular approach was chosen, tricky invariants.
- **Section comments** to give structure to long methods.
- **Cross-module hacks** — when forced into a dependency that can't be
  eliminated, document it at both ends so future readers know why.

Scope:

- `api` package: full javadoc on every public class and method.
- Internal code: javadoc on classes and non-trivial methods.

## Test conventions

Use `// when` and `// then` comments to separate test phases.

## Worktrees

When creating git worktrees, always use relative paths in both
`.git` (the worktree's gitdir pointer) and `.git/worktrees/<name>/gitdir`.
The repo may be inside a container whose absolute paths differ from the
host.

## Workflow

- Never commit unless explicitly told to commit.
- Never start implementing unless explicitly told to start. The user
  answering scope, design, or clarifying questions is NOT a green light —
  it refines the plan, nothing more. Wait for an explicit imperative
  ("go", "start", "implement it", "do it", "proceed") before any
  state-changing tool use: `TaskCreate`, `Edit`, `Write`, or `Bash` that
  mutates the working tree. `Read`, `Grep`, and other read-only
  exploration are fine while planning. If you're unsure whether you have
  the green light, you don't — present the plan, ask "ready to start?",
  and wait.
- **Review feedback is not a green light.** When the user gives review
  comments, corrections, or feedback on a proposal: summarize your
  updated findings and proposed solution, then ask for confirmation
  before coding. The user pointing out a problem or asking "what about
  X?" means "revise the plan", not "go fix it". Only start coding after
  an explicit go-ahead.
- **Think before coding.** Make your assumptions explicit up front; when
  in doubt, ask rather than guess. When multiple interpretations exist,
  surface them — don't silently pick one. If a simpler approach exists,
  say so and push back when warranted.
- **Define success criteria before starting.** Turn the task into
  something checkable — "add validation" becomes "cover the invalid
  inputs with failing tests, then satisfy them"; "fix the bug" becomes
  "capture it in a failing test first, then make that test green". For
  multi-step work, state a brief plan with a verify step for each step.
  Strong criteria let you loop to done without constant clarification.
- Start with the simplest implementation that passes the tests. Add
  complexity (helper methods, guards, extra abstractions) only when a
  failing test or concrete scenario forces it — not preemptively. No
  error handling for scenarios that can't occur; if a 200-line draft
  would work as 50, throw it away and write the 50.
- **Make surgical changes.** Each changed line should be justifiable
  straight from the request. Don't improve adjacent code, refactor what
  isn't broken, or restyle to taste — match the existing style even
  where you'd differ. Remove imports/variables/functions your change
  orphaned; leave pre-existing dead code alone (mention it, don't
  delete it).
- When a fix could go in two places, fix the root cause, not the
  symptom. A defensive check that filters out bad data is a sign the
  producer should be fixed instead.
- Do not introduce new patterns or invent new abstractions (factories,
  builders, strategy/visitor shapes, helper layers, new base classes,
  new interfaces, generic wrappers, etc.) unless the ticket explicitly
  calls for it OR you have asked and gotten explicit confirmation.
  Solving the stated problem with the existing shapes in the codebase
  is the default. "It would be cleaner" or "it would scale better" is
  not sufficient justification — propose it, wait for a yes, then
  implement.
