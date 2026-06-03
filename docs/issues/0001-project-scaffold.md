# Project Scaffold

## What to build

Set up the Maven project structure: a parent POM with a single child module
(`json-schema-gen-core`), package skeleton, CLAUDE.md, .gitignore, and
empty `docs/adr` and `docs/prd` folders.

## Acceptance criteria

- [ ] Parent POM exists with packaging `pom` and one declared module
- [ ] `json-schema-gen-core` module builds with `mvn clean verify`
- [ ] Root Java package `se.plilja.jsonschemagen` exists with `api` and
      `internal` sub-packages
- [ ] CLAUDE.md at repo root documents purpose, build commands,
      architecture overview, and package conventions
- [ ] .gitignore covers Maven `target/` and common IDE files
- [ ] `docs/adr/` and `docs/prd/` directories exist

## Blocked by

None — can start immediately.
