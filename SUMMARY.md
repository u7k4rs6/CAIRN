# Cairn: build summary

Cairn is a self-hosted Git hosting and collaboration platform, built as a portfolio and
interview artifact. All nine milestones from the build brief are complete: a real
content-addressable version control engine (`cairn-vcs`), a Git-over-HTTP transfer
layer with real smart-negotiation (`cairn-transfer`), a permissioned collaboration
platform (`cairn-api`), and a web UI (`web`). The engine is cross-checked against a
real `git` binary throughout, not just against its own tests.

## What's built, milestone by milestone

### M1: object store and DAG
Content-addressable `Blob`/`Tree`/`Commit`/`Tag` objects, hashed with Git's exact
on-wire encoding (SHA-1 over `"<kind> <len>\0<body>"`). `ObjectStore` (Strategy) with
a loose-object implementation. `FileRefStore`, `Head`, a naive `RevWalk`, an `Index`
staging area, and a `Repository` porcelain facade (`init`/`add`/`commit`/`log`).
Blob and tree hashes verified byte-for-byte against `git hash-object` / `git write-tree`.

### M2: diff and merge
`MyersDiff` (linear-space middle-snake divide-and-conquer), validated against a
brute-force LCS oracle over 800+ randomized cases. `FileMerge` (diff3-style hunk-level
three-way text merge). `TreeMerger`/`TreeFlattener` (tree-level three-way merge with
per-path conflict detection). `MergeEngine` (fast-forward detection, recursive
merge-base folding for criss-cross history). `Repository` gained
`createBranch`/`checkout`/`diff`/`merge`. End-to-end tests cover a clean merge, a
fast-forward, a genuine conflict (reported, not lost), and a real criss-cross history
resolved via recursive merge base.

### M3: generation numbers
`GenerationStore` (Strategy) + `FileGenerationStore`. `Ancestry` rewritten on
generation numbers: O(1) negative ancestry, a pruned positive walk, a merge-base fast
path for the direct-ancestor case, with the general criss-cross case falling back to
M2's proven-correct full enumeration (an honest, stated complexity boundary, not an
overclaimed win). Instrumented proof: negative ancestry on a 1000-commit chain touches
at most 2 objects.

### M4: packfiles and delta
`DeltaCodec` implements Git's real delta instruction format. `PackWriter`/`PackReader`
implement Git's actual packfile format (`PACK` magic, version 2, REF_DELTA, zlib
deflate, trailing SHA-1 checksum, bounded delta-chain depth). Cross-checked against
real `git`: a Cairn-written pack (including a 5-deep delta chain) was accepted by
`git index-pack --stdin` and passed `git fsck --full` and `git verify-pack -v` with the
expected chain lengths.

### M5: transfer with negotiation
`PktLine` framing, `RefAdvertisement` (with `HEAD`/`symref` so a real clone knows what
to check out), `UploadPackHandler` (want/have negotiation) and `ReceivePackHandler`
(ref-update commands, atomic writes). A minimal Spring Boot app exposes
`info/refs`/`git-upload-pack`/`git-receive-pack`. **Proven with a real installed `git`
binary**, not just unit tests: clone, push, clone, a second push, then fetch + merge,
finishing with `git fsck --full`. Found and fixed a real interop bug this way: this
environment signs commits by default, and unrecognized commit headers (`gpgsig`) were
being silently dropped, corrupting the recomputed hash on round-trip.

### M6: permissions
Domain model (`User`, `Organization`, `Team` as a Composite hierarchy, `Repo`,
`CollaboratorGrant`, `TeamGrant`, `BranchProtectionRule`, `PersonalAccessToken`).
`DefaultPermissionResolver` implements the security doc's `effective_role` algorithm
verbatim, against a swappable `GrantLookup` (Repository pattern). `ReceivePackHandler`
gained a pluggable `RefUpdateAuthorizer`: every ref update is authorized before any are
written, one denial rejects the whole push atomically. `GitHttpController` challenges
anonymous callers with 401 and masks denied authenticated callers behind 404. Verified
against a real git client, which is how a real bug was found: git only sends Basic auth
after a 401 challenge, not preemptively from a credentialed URL.

### M7: collaboration
`PullRequestState`/`IssueState` as enum-based State pattern instances.
`PullRequestService.merge` wires `PermissionResolver`, `BranchProtectionRule`,
`MergeEngine`, and the state machine together, supporting `MERGE_COMMIT` and `SQUASH`
strategies. REST surface for issues (+ comments) and pull requests (+ reviews, + merge).
Tests cover the PRD's acceptance criterion directly: insufficient role blocks merge,
a clean merge produces a real commit and transitions state, merging twice is rejected
by the state machine, and branch protection blocks merge until approved.

### M8: web UI
`RepoContentController` adds tree/blob/commit-history/commit-diff read endpoints,
gated on read access like everything else. A Next.js 16 (App Router, React 19,
Tailwind v4) frontend: repo browsing at any ref/path, commit history, commit diffs,
issue list/detail, PR list/detail with review and merge actions. Server Components
fetch directly from the API; a few client islands (auth, merge, review, comment) hold
the write actions. `DevDataSeeder` (a `seed` Spring profile) seeds a demo repo, issue,
and PR so the UI has something real to browse. Verified end to end against a running
backend with `curl` and by loading every page.

### M9: paper-only stretch, plus a real Observer
Rather than leave Observer paper-only along with the rest of M9 (it was found to be
entirely unimplemented against the build brief's five named patterns), it's built as a
small real feature: `ActivityListener`/`ActivityPublisher`/`ActivityEvent`, two
independent listeners (`InMemoryActivityFeed`, `LoggingActivityListener`), wired into
issue/PR creation and PR merge, with a read-gated `GET .../activity` endpoint. The
remaining stretch items (trigram search, partial clone, reachability bitmaps, webhook
delivery, sharding) stay paper-only; see "Known gaps" below for their design writeups.

## Test status

| Module | Tests | Status |
|---|---|---|
| `cairn-vcs` | 65 | passing |
| `cairn-transfer` | 10 | passing |
| `cairn-api` | 46 | passing |
| `web` | none (no automated suite) | `npm run build` succeeds; manually verified end to end |

Total: 121 automated JVM tests, all green. Several of the most important ones are
real-git-binary integration tests (`GitHttpIntegrationTest`), not mocks of git's
behavior: an actual `git clone`/`push`/`fetch`/`merge`/`fsck` round trip against the
running embedded server.

## Design patterns, where they pay rent

- **Strategy:** `ObjectStore` (loose/packed), `DiffStrategy` (Myers), `PermissionResolver`,
  `MergeStrategy` (merge commit/squash).
- **State:** `PullRequestState`, `IssueState`: enum constants that each override only
  the transitions legal from that state, so `MERGED` is structurally terminal.
- **Composite:** `Team`, nested arbitrarily deep, walked with a depth cap and visited
  set (safe against both a deep chain and a genuine cycle).
- **Observer:** `ActivityListener`/`ActivityPublisher` fan out repo events (issue
  opened, PR opened, PR merged) to independent listeners without either side knowing
  about the other.
- **Repository:** Spring Data JPA repositories for persistence generally; a narrow
  hand-rolled `GrantLookup` interface specifically for `PermissionResolver`, tested
  with a zero-Spring in-memory fake.

## Build, test, and run

```
./gradlew build              # compile + test everything
./gradlew test                # test all three JVM modules
./gradlew :cairn-vcs:test     # engine tests only
./gradlew :cairn-api:test     # platform tests only

# Run the API with seeded demo data (a repo, an issue, a PR, and a printed token):
./gradlew :cairn-api:bootRun --args='--spring.profiles.active=seed'

# Run the API against a real git client with no seed data:
./gradlew :cairn-api:bootRun

# Run the web UI (expects the API on localhost:8080, see web/.env.local):
cd web && npm install && npm run dev
```

The web UI is at `http://localhost:3000`. With the `seed` profile, browse
`http://localhost:3000/acme/demo` directly, or push your own repo:

```
git remote add origin http://localhost:8080/<owner>/<repo>.git
git push origin main
```

## Decisions and assumptions

Every judgment call is recorded with its rationale in `DECISIONS.md`, organized by
milestone, newest first. The recurring theme across all nine milestones: whenever a
milestone's correctness claim could be checked against the real thing (real `git`,
a real HTTP round trip, a real browser client), it was, and several real bugs were
only found this way, not by unit tests written against the code's own assumptions:

- **M1/M5:** Git's exact object encoding and an unrecognized `gpgsig` commit header
  silently dropped and corrupting round-trip hashes.
- **M4:** packfile format cross-checked against `git index-pack`/`fsck`/`verify-pack`.
- **M5:** the ref advertisement needing `HEAD`/`symref` or a real clone checks out
  nothing.
- **M6:** git only sends Basic auth after a 401 challenge, and a JPQL implicit inner
  join silently dropping every user-owned repo from a lookup.
- **M8:** Jackson serializing plain-accessor domain classes as `{}`, the fix then
  needing `@JsonIgnore` on `passwordHash`/`tokenHash`, a brand-new empty repo crashing
  instead of rendering empty, and React's reserved `ref` prop name.

`PROGRESS.md` tracks milestone-by-milestone status and test counts as the project was
built; `DECISIONS.md` is the full assumptions-and-rationale log.

## Known gaps and next steps

**Scope cuts made under time pressure, most valuable first:**

- **Trigram code search (PRD Tier 3 / FR-SEARCH-1) is not built.** Design: tokenize
  every blob's content into overlapping character trigrams, maintain an inverted index
  (trigram to posting list of blob ids/positions), and answer a substring query by
  intersecting the posting lists for the query's trigrams, then verifying each
  candidate against the actual content (cheap, since the index has already narrowed
  the candidate set from "every blob" to "blobs sharing all the query's trigrams").
  Cost: O(total code) to build the index, then a query is near
  O(intersected postings + candidate verification) instead of an O(total code) grep
  per query (architecture doc, section 10). The real cost is index staleness on every
  push, which needs either synchronous index update on receive-pack (simple, slows
  pushes) or an asynchronous job (architecture doc's job-worker tier, not built).
  Highest-value next step of everything listed here, since it is the one item the PRD
  scoped into v1 rather than marking paper-only.
- **Partial clone / sparse checkout (paper-only per PRD).** Real Git's approach: a
  "promisor remote" lets a clone fetch only commits and trees up front, deferring blob
  fetches until a file is actually read (a filter spec like `blob:none`). Cairn's
  transfer layer would need: (1) a filter parameter in the negotiation request, (2) a
  way to mark certain objects as "promised" (known to exist, not locally present) in
  `ObjectStore`, and (3) an on-demand fetch path triggered by a cache miss during
  checkout. High complexity for narrow marginal signal once negotiation and packfiles
  already exist (per PRD's own framing); deferred.
- **Reachability bitmaps (paper-only per PRD).** Precompute, per commit, a bitmap over
  all objects reachable from it (one bit per object, in a fixed global ordering).
  Ancestry and merge-base become bitwise AND/OR over precomputed bitmaps instead of a
  graph walk, at the cost of building and maintaining (recomputing on every new
  commit, or incrementally) a bitmap the size of the object count. Pays off once the
  repository is large enough that even generation-number-pruned walks touch a lot of
  commits; for this project's scale, generation numbers (M3) already capture the
  practical win without the bitmap-maintenance cost.
- **Outbound webhook delivery (paper-only per PRD).** The `ActivityListener` seam
  built in M9 is exactly where this plugs in: a `WebhookDeliveryListener` that, on
  each `ActivityEvent`, looks up subscriber URLs for the repo and POSTs a signed
  payload (HMAC over the body with a per-webhook secret, verified by the receiver),
  with retry/backoff on failure and a dead-letter path after repeated failures. Not
  built because HTTP delivery, retry semantics, and signing are real additional
  machinery beyond the Observer wiring itself, which is what this milestone was
  actually testing.
- **Repository sharding (paper-only, HLD topic per PRD).** Repos are independent
  object-storage namespaces (each `owner/name` already maps to its own on-disk
  directory via `RepositoryRegistry`), so they shard naturally by repo id or name
  hash across nodes or object-store buckets. A routing layer (a lookup table or a
  consistent-hash ring) maps `{owner}/{name}` to a shard; the metadata store
  (`Repo.id`) already has a natural shard key sitting right next to the object
  storage it describes.
- **A minimal CI trigger and project boards/wikis/discussions** are out of scope for
  v1 per the PRD's own "out of scope" list (section 10); not attempted.
- **`REBASE` merge strategy is not implemented** (M7 gap): `MERGE_COMMIT` and
  `SQUASH` both reduce to "compute one merged tree, write one commit"; a real rebase
  needs to replay each source commit individually against a moving target, re-running
  a three-way merge per replayed commit. Named as a gap in M7's `DECISIONS.md` entry.
- **No `GET .../pulls/{number}` single-resource endpoint** (M8): the frontend fetches
  the list and finds the matching PR by number. Fine at this scale; would need
  revisiting if the list payload ever needs to shrink.
- **`spring.jpa.open-in-view` stays at its default (`true`)** (M8): several
  controllers rely on lazy JPA association access during response serialization.
  Disabling it safely needs an audit of every such access first, which time did not
  allow; left as a known gap rather than risk trading one bug for another.
- **Auth in the web UI is a username + personal access token in `localStorage`**, not
  a real session/cookie/CSRF flow, matching what the backend actually supports
  (Basic auth with a PAT).
- **`DiffView` renders every line unvirtualized** (M8): correct at demo scale, not
  representative of what a real host needs at file-scale diffs.

## Repository layout

```
cairn-vcs/       standalone VCS engine (zero dependencies on web/persistence)
cairn-transfer/  Git-over-HTTP smart transfer (pkt-line, negotiation, pack I/O)
cairn-api/       Spring Boot platform: permissions, collaboration, REST, Git HTTP
web/             Next.js 16 web UI
docs/            the four source-of-truth specs (PRD, architecture, security, frontend)
PROGRESS.md      milestone-by-milestone status and test counts, as built
DECISIONS.md     every judgment call, with rationale, newest first per milestone
SUMMARY.md       this file
```
