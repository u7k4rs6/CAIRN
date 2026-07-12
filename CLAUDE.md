# Cairn: Claude Code build brief

Build **Cairn**, a self-hosted Git hosting and collaboration platform in Java, following the four specs in `docs/` (PRD, technical architecture, security and access, frontend spec). They are the source of truth; this brief only governs how execution proceeds.

This is an interview and portfolio artifact, so class design quality matters as much as working code. Idiomatic, well-named Java with the design patterns from the architecture doc visible and deliberate (Strategy, State, Composite, Observer, Repository).

The centerpiece is a real content-addressable version-control engine (object store, commit DAG, packfiles with delta, transfer negotiation, three-way merge), not a database pretending to be Git.

## Ground rules

- Java 17+, Gradle multi-module, module layout: `cairn-vcs`, `cairn-transfer`, `cairn-api`, `web`. `cairn-vcs` is a standalone library with zero dependencies on the web or persistence layers; dependencies point inward only.
- Keep the tree green: after each milestone the project compiles and all tests pass, then commit.
- Each milestone ships with unit tests; PRD acceptance criteria become integration tests.
- Every core operation (object put/get, packed reconstruction, ancestry and merge-base, diff, merge, negotiation, permission lookup, search) carries a complexity-and-tradeoff note in Javadoc or `COMPLEXITY.md`.
- Any markdown generated uses no em dashes.

## Build order

M1 object store and DAG -> M2 diff and merge -> M3 generation numbers -> M4 packfiles and delta -> M5 transfer with negotiation -> M6 permissions -> M7 collaboration -> M8 web UI -> M9 stretch (paper-only stubs).

## Scope guardrails

Finish M1 to M8 before touching M9. A working, tested engine plus a thin platform plus a minimal UI beats a broad, broken everything. If blocked, stabilize what exists, keep it green, and record the gap in `PROGRESS.md` / `DECISIONS.md`.

## Autonomy protocol

- `PROGRESS.md`: current milestone, what is done, what is next, test status.
- `DECISIONS.md`: every assumption or judgment call with a one-line rationale.
- Commit after every milestone and after any significant unit of work.

## Commands

```
./gradlew build          # compile + test everything
./gradlew :cairn-vcs:test
./gradlew :cairn-api:bootRun
cd web && npm install && npm run dev
```
