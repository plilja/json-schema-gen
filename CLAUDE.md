# gjuton

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

The three-phase pipeline, schema model design, generation strategy, and
package layering with its allowed-dependency rules are documented in:

@docs/architecture.md

## Testing

Integration tests are parameterized and driven by schema files in
`src/test/resources/schemas/`. Adding a schema file is all that's needed to add
a test case.

## Issues

Issues are tracked on GitHub. After completing work on an issue, close
it on GitHub, checking off acceptance criteria and revising any
descriptions that no longer match the implementation.

## Code conventions

Code style (Google-modified, `var` usage, line-breaking), design conventions
(deep methods), documentation conventions (Ousterhout-style javadoc), and test
conventions are documented in:

@docs/code_convention.md

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
