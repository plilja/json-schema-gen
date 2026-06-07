# Build and Test Improvements

## What to build

`IntegrationTest` exposed a seed-dependent flake in
`integer-multiple-of.json`: at ~0.1% per test case, an `integer` schema
with `multipleOf` occasionally generates a value that fails JSON Schema
validation. The flake was hidden at 20 iterations and surfaces ~10% of
runs at 100. Iteration count has been raised to 200 to make the flake
more visible, but the real fix is to root-cause and remove it.

This issue covers that diagnosis plus broader build/test improvements
identified along the way.

## Acceptance criteria

- [x] Root-cause the `integer-multiple-of` flake (likely in
      `NumericGenerator.randomLong()` / `snapUp` / `snapDown`),
      reproduce with a pinned seed, fix it, and add a regression test
- [x] Investigate parallelising the integration test suite
      (`junit.jupiter.execution.parallel.enabled` or surefire forking)
      so iteration count can grow without proportional wall-clock cost
- [x] Re-tune `IntegrationTest` iteration count once the flake is fixed
      and parallelisation is in place — aim for high coverage at low cost
- [x] Survey other build improvements: incremental compilation, surefire
      reuse-forks, test result reporting, CI caching, etc., and apply
      the ones that pay off

## Resolution

- **`integer-multiple-of` flake** — root cause was in
  `NumericGenerator.effectiveMin/effectiveMax`: when no `minimum`/`maximum`
  was set, the implicit range fell back to `Long.MIN_VALUE` /
  `Long.MAX_VALUE - 1`. The RANDOM phase then sampled uniformly across
  the full Long range, almost always producing values above 2^53. Those
  values are exact multiples of `m` in long arithmetic, but any validator
  that does the `multipleOf` check in IEEE 754 double precision loses
  precision above 2^53 and reports a non-zero remainder. Fix: clamp the
  implicit unbounded range to `±(2^53 - 1)` only when `multipleOf` is
  set; plain `integer` schemas keep the wide range so consumers are
  still stressed by large numbers. Regression test:
  `NumericGeneratorTest.unboundedMultipleOfStaysWithinJsonSafeIntegerRange`.
- **`string-pattern-bounded` flake** — surfaced once iteration count
  was raised. `StringGenerator.generateFromPattern()` retried up to 100
  times to fit length bounds, then silently fell back to
  `rgxGen.generate(random)` whose output could violate `maxLength`. Two
  fixes: (a) when `maxLength` is set, configure rgxgen's
  `INFINITE_PATTERN_REPETITION` to `maxLength` so `+`/`*` quantifiers
  stay within bounds, and (b) replace the silent fallback with a
  thrown `UnsatisfiableSchemaException` (new public exception in a new
  top-level `errors` package — see CLAUDE.md / ADR-0003). Regression
  test: `StringGeneratorTest.unboundedQuantifierPatternStaysWithinMaxLength`.
- **Test structure refactor** — generation moved into the
  `@MethodSource` so each `(schema, invocation)` pair is materialised
  once at fixture setup instead of regenerated for every parameterised
  invocation. Schemas are processed in parallel via `parallelStream()`
  during setup. Tests themselves run sequentially. This makes raising
  the iteration count cheap.
- **Iteration count** — raised from 200 to 1000. 25 schemas × 1000
  invocations ≈ 25 000 validations per run, ≈ 1.9s wall-clock. Sized
  with headroom: the suite is expected to grow 3–5× in schema count;
  at 5× this projects to ~9.5s.
- **Build cleanups** — compiler config switched to
  `maven.compiler.release` (silences the `-source 21` warning, enforces
  target-21 API). `slf4j-simple` pulled into the core module test scope
  (silences networknt's "no providers" noise).
- **`fast` profile** — added a Maven profile that sets
  `checkstyle.skip=true` and `spotbugs.skip=true`. `mvn verify -Pfast`
  drops from ~14s to ~9s (≈35% faster). CLAUDE.md instructs using
  `-Pfast` for the inner loop and plain `mvn verify` once before
  declaring a task done. Style and static analysis still gate the
  final pre-commit run.
- **JUnit Jupiter parallel — tried, dropped.** After the test
  structure refactor moved generation to the `@MethodSource`, parallel
  test execution was retried via `junit-platform.properties`
  (`parallel.enabled=true`, `mode.default=concurrent`,
  `mode.classes.default=same_thread`). Within-class parallelism shaved
  IntegrationTest from 1.8s to 1.6s — inside shell-timer noise
  end-to-end. Removed: no measurable benefit today, and parallel
  execution invites a class of subtle bugs (shared `Random`, static
  state, ordering) that we'd just spent the issue hunting. Cheap to
  re-add when the suite actually gets slow.
- **mvnd (Apache Maven Daemon) — recommended dev tool** — biggest
  per-run speedup we measured (~40–50% off warm runs). Keeps a JVM
  warm between invocations. Installed once per machine, not pinned
  per-repo. CLAUDE.md documents the install steps and makes
  `mvnd verify -Pfast` the primary inner-loop command. Plain
  `mvn verify` stays as the pre-commit gate (and remains the only
  command CI runs — daemon gives no benefit on cold-start CI).
- **Skipped** — CI caching (no CI configured); test result reporting
  (no consumer); Maven build cache extension (single-module build);
  Maven Wrapper (`mvnw`) — a separate concern (version pinning, not
  speed); candidate for its own follow-up issue.

## Build-speed experiments — results

Each row is a fresh `mvn clean verify` (3-run median). Changes are
cumulative top-to-bottom unless noted.

| Configuration                                  | Wall-clock |
|------------------------------------------------|------------|
| Baseline `mvn verify`                          | 13–15 s    |
| `mvn verify -Pfast` (skips checkstyle+spotbugs)| 8–9 s      |
| `+ JUnit Jupiter parallel`, fast               | 8–9 s      |
| `+ JUnit Jupiter parallel`, full               | 12–14 s    |
| `+ surefire forkCount=1C`, fast (reverted)     | 10–11 s ⚠  |
| `+ surefire forkCount=1C`, full (reverted)     | 14–15 s ⚠  |
| `mvnd verify -Pfast` (warm)                    | 5 s        |
| `mvnd verify` (warm, full)                     | 7–9 s      |

- **Surefire `forkCount=1C reuseForks=true` — reverted.** Regressed
  fast from 8–9s to 10–11s and full by ~1s. With 16 cores and a
  test load dominated by one huge `IntegrationTest` class, 15 forks
  sit idle while paying JVM-startup cost for nothing. Right move for
  evenly-distributed test classes; wrong move for ours.
- **mvnd (Apache Maven Daemon) — best per-run speedup, not adopted as
  the default.** Cuts warm-run wall-clock by ~40–50% by keeping a JVM
  warm between invocations. Tradeoffs: extra tool to install
  per-developer, no benefit on CI (always cold-starts), and adds a
  layer of indirection when debugging Maven oddities. Recommendation:
  use as an opt-in developer tool — `alias m=mvnd` for hot loops —
  but don't bake into project scripts.

## New public api surface

- `se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException` —
  thrown by `JsonSchemaGenerator.generate()` when the generator cannot
  produce a value satisfying the schema. Lives in a new top-level
  `errors` package (added to the layering in CLAUDE.md and ADR-0003)
  so internal generators can throw it directly without depending on
  `api`.

## Notes

- The reason we generate with system-time seeds (rather than fixed
  seeds) is to surface seed-dependent bugs across runs. Don't switch to
  deterministic seeds — that would hide exactly this class of bug.
- Iteration count is a knob: higher catches flakes faster but is wasted
  work once a bug is fixed. The current value targets ~95%+ catch rate
  for ~0.1%-per-case flakes while keeping `mvn verify` snappy as the
  schema count grows.

## Blocked by

None.
