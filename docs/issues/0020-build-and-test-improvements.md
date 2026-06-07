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

- [ ] Root-cause the `integer-multiple-of` flake (likely in
      `NumericGenerator.randomLong()` / `snapUp` / `snapDown`),
      reproduce with a pinned seed, fix it, and add a regression test
- [ ] Investigate parallelising the integration test suite
      (`junit.jupiter.execution.parallel.enabled` or surefire forking)
      so iteration count can grow without proportional wall-clock cost
- [ ] Re-tune `IntegrationTest` iteration count once the flake is fixed
      and parallelisation is in place — aim for high coverage at low cost
- [ ] Survey other build improvements: incremental compilation, surefire
      reuse-forks, test result reporting, CI caching, etc., and apply
      the ones that pay off

## Notes

- The reason we generate with system-time seeds (rather than fixed
  seeds) is to surface seed-dependent bugs across runs. Don't switch to
  deterministic seeds — that would hide exactly this class of bug.
- Iteration count is a knob: higher catches flakes faster but is wasted
  work once a bug is fixed. Parallelisation softens the cost.

## Blocked by

None.
