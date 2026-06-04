# Checkstyle Baseline

## What to build

Add Checkstyle static analysis to the build using Google's Java style guide
so that consistent code style is enforced automatically during `mvn verify`.

## Rules

1. **Checkstyle runs on verify** — `checkstyle:check` is bound to the `verify`
   phase and fails the build on any violation.
2. **Google style** — `google_checks.xml` (bundled with the plugin) is used as
   the configuration; no external file is needed.

## Acceptance criteria

- [x] `maven-checkstyle-plugin` added to root `pom.xml`
- [x] `google_checks.xml` set as the config location
- [x] Existing source files reformatted to comply with Google style (2-space indentation)
- [x] `mvn verify` passes with no Checkstyle violations
