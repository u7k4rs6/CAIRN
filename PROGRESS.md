# Progress

## Current milestone
All milestones (M1 to M9) complete.

## Done
- Gradle multi-module scaffold: `cairn-vcs`, `cairn-transfer`, `cairn-api`, `settings.gradle`, root `build.gradle`.
- `cairn-vcs` (M1): `Blob`, `Tree`, `Commit`, `Tag` objects with Git-compatible SHA-1 encoding;
  `ObjectId`, `ObjectKind`, `FileMode`, `TreeEntry`, `PersonIdent`; `GitObjects` deserialization factory.
  `ObjectStore` (Strategy) with `LooseObjectStore`. `RefStore` (`FileRefStore`) and `Head` (symbolic ref).
  `RevWalk` (naive DAG walk). `Index` (staging area) and `TreeBuilder` (flat paths to nested trees).
  `Repository` porcelain facade: `init`, `add`, `commit`, `log`.
- Cross-checked against real `git`: blob and tree hashes for known content match `git hash-object`
  and `git write-tree` exactly.
- `cairn-vcs` (M2): `MyersDiff` (linear-space middle-snake divide-and-conquer), validated against
  a brute-force LCS oracle over 800+ randomized cases plus the classic Myers paper example.
  `FileMerge` (diff3-style hunk-level three-way text merge). `Ancestry` (naive ancestor/merge-base
  walk, multi-base for criss-cross). `TreeMerger` + `TreeFlattener` (tree-level three-way merge,
  per-path add/add and modify/delete whole-file conflicts, modify/modify hunk-level conflicts via
  `FileMerge`). `MergeEngine` (fast-forward detection, recursive merge-base folding). `TreeDiff`
  (structural tree diff). `Repository` gained `createBranch`, `checkout`, `diff`, `merge` porcelain.
- End-to-end porcelain tests cover the PRD MVP gate directly: clean divergent-branch merge,
  fast-forward, a conflicting change reported (not lost, and no commit created), and a genuine
  criss-cross history (two lowest common ancestors) resolved via the recursive merge base.

- `cairn-vcs` (M3): `GenerationStore` (Strategy) + `FileGenerationStore` (flat-file persistence,
  loaded into memory). `GenerationNumbers` (incremental `computeAndStore` wired into `commit`/`merge`,
  plus a bulk `recomputeAll` for pre-existing history). `Ancestry` rewritten on top of generation
  numbers: O(1) negative ancestry, a pruned positive walk, and a merge-base fast path for the
  direct-ancestor case; the general criss-cross case keeps M2's proven-correct full enumeration
  (documented honestly as a scope boundary, not a claimed complexity-class win).
- Instrumented proof (a counting `ObjectStore` wrapper standing in for "instrument the walk"):
  negative ancestry on a 1000-commit chain touches at most 2 objects; a merge-base 10 commits back
  from the tip of that same chain touches well under a quarter of the chain, not the whole history.

- `cairn-vcs` (M4): `DeltaCodec` implements Git's real delta instruction format (COPY/INSERT,
  varint sizes), with the copy/insert script itself found by reusing `MyersDiff` at the byte
  level (a documented tradeoff: implementation/correctness confidence over Git's own linear-time
  rolling-hash matcher). `PackWriter`/`PackReader` implement Git's actual packfile format
  (`PACK` magic, version 2, per-entry type+size header, zlib-deflated bodies, REF_DELTA against
  a same-kind prior object, trailing SHA-1 checksum) with bounded delta-chain depth (cap 50).
  `PackedObjectStore` is a read-only `ObjectStore` view over a loaded pack.
- Cross-checked against real `git`, not just our own reader: a Cairn-written pack (both a
  plain multi-object pack and one with a 5-deep REF_DELTA chain) was accepted by
  `git index-pack --stdin`, passed `git fsck --full` and `git verify-pack -v` with the exact
  expected delta chain lengths, and every object's content matched via `git cat-file -p`.

- `cairn-transfer` (M5): `PktLine` framing, `RefAdvertisement` (with `HEAD`/`symref` so a real
  clone knows which branch to check out), `ObjectClosure` (full object reachability, not just
  commits, for the negotiation formula), `UploadPackHandler` (want/have parsing, `NAK` + pack
  response) and `ReceivePackHandler` (ref-update commands, pack unpacking, report-status).
  `cairn-api`: a minimal Spring Boot app exposing `info/refs`, `git-upload-pack`, and
  `git-receive-pack`, with a permissive `SecurityConfig` staged to be replaced by M6.
- **A real end-to-end test drives an actual installed `git` binary** against the running
  embedded server: clone of an empty repo, push, clone, a second push, then `fetch` + `merge`
  picking up the new commit, finishing with `git fsck --full` on the resulting clone.
- **Found and fixed a real interoperability bug this way**: this environment signs commits by
  default (SSH `gpgsig` header), which `Commit.parse` was silently dropping as an unrecognized
  header, changing the recomputed id on round-trip and breaking the real clone with
  "not a commit: ...". Fixed by preserving unrecognized commit headers verbatim; added a
  byte-exact regression test using the real captured `gpgsig` bytes.

- `cairn-api` (M6) domain model: `User`, `Organization`, `Team` (nested, Composite structure),
  `Repo` metadata, `CollaboratorGrant`, `TeamGrant`, `BranchProtectionRule`, `PersonalAccessToken`,
  `Principal` (sealed: anonymous/user). `Role` and `Visibility` as ordered enums.
- `DefaultPermissionResolver` implements the security doc's `effective_role` algorithm verbatim,
  against a narrow `GrantLookup` interface (Repository pattern: a `FakeGrantLookup` in tests, a
  `JpaGrantLookup` for real persistence). Bounded team walk (depth cap + visited set) confirmed
  safe against both a deep chain and a genuinely cyclic team structure.
- `ReceivePackHandler` (cairn-transfer) gained a pluggable `RefUpdateAuthorizer`: every ref update
  is authorized before any are written, and a single denial rejects the whole push atomically
  (security doc, section 4.3), matching the spec's pseudocode exactly. `GitReceivePackAuthorizer`
  (cairn-api) is the real implementation: requires write, then enforces branch protection
  (no force-push, detected via real ancestry; no deletion; a role floor per branch).
  `GitHttpController` gates every path on `effective_role`, challenging anonymous callers with
  `401`/`WWW-Authenticate` (so a real git client retries with credentials) and masking denied
  authenticated callers behind `404`.
- Personal access tokens (SHA-256 hash, high-entropy secret) authenticate Git-over-HTTP via Basic
  auth; a minimal `POST /api/repos` lets a user create a repo with an explicit visibility (FR-REPO-1).
- The PRD's private-repo acceptance criterion is proven twice: as a fast unit test
  (`DefaultPermissionResolverTest`) and against the real HTTP boundary with a real git client
  (`GitHttpIntegrationTest`), including the discovery (via that real-client test) that git only
  sends Basic auth after a `401` challenge, not preemptively from a credentialed URL - and a
  separate bug where a hand-written JPQL query's implicit inner join was silently dropping every
  user-owned repo from lookups.

- `cairn-api` (M7): `PullRequestState` and `IssueState` as enum-based State pattern instances
  (each constant overrides only the actions legal from it; `MERGED` overrides nothing, making
  it structurally terminal). `PullRequest`, `Issue`, `Review`, `Comment` domain entities.
  `PullRequestService.merge` wires every earlier milestone together: `PermissionResolver`
  (write required), `BranchProtectionRule` (approval-before-merge), `MergeEngine` (the real
  three-way merge), and the state machine's own legal-transition check, in that order, and
  supports `MERGE_COMMIT` and `SQUASH` strategies (`REBASE` is a documented gap, see DECISIONS.md).
  Minimal REST surface: `POST/GET /api/repos/{owner}/{name}/issues` (+ comments),
  `POST/GET /api/repos/{owner}/{name}/pulls` (+ reviews, + merge).
- Tests cover the PRD acceptance criterion directly: a PR cannot be merged by a principal
  lacking write, a clean merge produces a real commit and transitions the PR to `MERGED`,
  merging an already-merged PR is rejected by the state machine itself, and branch protection's
  approval requirement blocks merge until the PR reaches `APPROVED`.

- `cairn-api` (M8) content endpoints for the frontend: `RepoContentController` adds
  `GET .../tree/{ref}/**`, `.../blob/{ref}/**`, `.../commits/{ref}`, and `.../commit/{sha}`
  (full diff, with resolved before/after line content per changed file, not just edit ranges).
  All gate on `effective_role >= read`, same as the Git HTTP endpoints.
- `web/`: a Next.js 16 (App Router, React 19, Tailwind v4) frontend over the M6-M7 APIs.
  Server Components fetch directly from `cairn-api` per the frontend spec's masked-404
  principle (a private repo and a nonexistent one render identically). Pages: repo home
  (tree + README), tree/blob browsing at an arbitrary ref and path, commit history, a single
  commit's diff, issue list/detail, PR list/detail with review submission and merge.
  A few client islands (`AuthBar`, `MergeBox`, `ReviewComposer`, `CommentComposer`) hold the
  token-bearing write actions; everything else is a plain server-rendered read.
- `DevDataSeeder` (Spring `seed` profile): seeds one demo repo with real commit history
  (including a diverging feature branch), an issue, a PR, and a working personal access
  token, printed to the console, so the UI has something real to browse without a manual
  `git push` first (`gradle :cairn-api:bootRun --args='--spring.profiles.active=seed'`).
- Found and fixed three real bugs that only a running browser client surfaces (not caught by
  the existing controller tests, which never round-tripped through Jackson's default config
  or a real HTTP client): Jackson was serializing every domain object as `{}` because the
  domain classes use plain accessors (`title()`, not `getTitle()`) and Jackson's default
  bean-introspection doesn't recognize those as getters (fixed via a global field-visibility
  Jackson config); that same fix would have then leaked `User.passwordHash` and
  `PersonalAccessToken.tokenHash` into every nested JSON reference to a user, so both fields
  got `@JsonIgnore`; and `resolveRef` on a brand-new, zero-commit repo was falling through to
  an unguarded `ObjectId.fromHex(ref)` and throwing, rather than returning the legitimate
  "nothing pushed yet" empty state (fixed by returning `Optional<ObjectId>`; regression test
  added). Also fixed a React-specific bug (the literal prop name `ref` is reserved by React
  even for plain data props; renamed to `gitRef`) and a `navigateToBlob` null-safety gap where
  a diff's added/deleted side can reference a path whose parent directory doesn't exist on
  the other side.
- Verified end to end against a real running backend (not just controller tests): every page
  and API route above was hit with `curl` and confirmed to return the right status/content,
  including the empty-repo case, before committing.

- `cairn-api` (M9) Observer pattern, built as a real minimal feature rather than left
  paper-only: `ActivityListener` (Observer) / `ActivityPublisher` (Subject) /
  `ActivityEvent`. Two independent listeners (`InMemoryActivityFeed`, a bounded
  per-repo feed; `LoggingActivityListener`) prove the fan-out is real. Wired into
  `PullRequestService.merge`, `IssueController.create`, `PullRequestController.create`.
  A new `GET /api/repos/{owner}/{name}/activity` endpoint (read-gated, same as every
  other repo endpoint) reads the feed back. A throwing listener is caught and logged,
  not propagated, so a broken observer can't fail the action that triggered it.
- The remaining M9 stretch items (trigram code search, partial clone/sparse checkout,
  reachability bitmaps, outbound webhook delivery, repository sharding) are documented
  on paper in `SUMMARY.md` with their design and tradeoffs, per the PRD's own framing
  of most of them as paper-only; code search is named honestly as a scope cut rather
  than a spec-sanctioned deferral (see DECISIONS.md).

## Test status
`cairn-vcs`: 65 tests, `cairn-transfer`: 10 tests, `cairn-api`: 46 tests. All passing (`gradle test`).
`web`: `npm run build` succeeds (no automated test suite; verified manually end-to-end, see M8).

## Next
All nine milestones complete. See `SUMMARY.md` for the full state of the project, exact
build/run commands, and known gaps.
