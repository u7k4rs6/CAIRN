# Cairn: build summary

Cairn is a self-hosted Git hosting and collaboration platform, built as a portfolio and
interview artifact. All nine milestones from the original build brief were completed
first, then a gap-closure audit and round of work closed most of what that first pass
under-reported or quietly dropped. The engine is cross-checked against a real `git`
binary throughout, not just against its own tests, and every controller endpoint added
in the gap-closure round was verified live against a running server (`curl` and, where
relevant, a real browser), not just by its own test suite.

## FR-by-FR completion audit

Per the gap-closure brief's completion gate: every functional requirement in the PRD,
its status, whether a real user can reach it end to end (not just "a domain model and
passing unit tests exist for it"), and the test id that proves it. A requirement not
reachable through a real endpoint and, where the frontend spec calls for one, a UI
screen is reported **not done** or **partial** here explicitly, even where the
underlying engine or domain work is real and correct.

| FR | Status | Reachable end to end | Test id |
|---|---|---|---|
| FR-VC-1 (blobs, content-addressed, dedup) | Done | Yes — every `commit`/`git push` | `GitObjectTest`, `LooseObjectStoreTest.puttingIdenticalContentTwiceStoresOnce` |
| FR-VC-2 (tree object) | Done | Yes — same paths; hash matches `git write-tree` | `GitObjectTest`, `RepositoryTest.nestedPathsBuildIntermediateTrees` |
| FR-VC-3 (commit object) | Done | Yes | `RepositoryTest.addAndCommitCreatesReachableObjects`, `.secondCommitRecordsFirstAsParent`, `GitObjectTest` (gpgsig round-trip) |
| FR-VC-4 (refs/HEAD) | Done | Yes — `Repository.createBranch/checkout`; a real `git clone` shows `HEAD`/`symref` | `RepositoryTest.initCreatesAnEmptyRepositoryPointingAtMain`, `GitHttpIntegrationTest` |
| FR-VC-5 (packfiles, bounded delta) | Done | Yes — any real `git push`/`fetch` | `PackRoundTripTest` (cross-checked against `git index-pack`/`fsck`/`verify-pack`) |
| FR-VC-6 (generation numbers) | Done | Yes, transparently — accelerates ancestry/merge-base on every merge and PR merge | `GenerationNumbersTest`, `AncestryGenerationNumberTest` (instrumented proof) |
| FR-VC-7 (log/diff, Myers) | Done | Yes — `GET .../commits/{ref}`, `.../commit/{sha}`; commit history and diff pages | `MyersDiffTest` (800+ oracle cases), `TreeDiffTest`, `RepoContentControllerTest.commitsReturnsHistory` |
| FR-VC-8 (merge base + three-way merge) | Done | Yes — `Repository.merge`; PR merge (all three strategies) | `RepositoryMergeTest`, `FileMergeTest`, `PullRequestServiceTest` |
| FR-XFER-1 (fetch sends only missing objects) | Done | Yes — real `git clone`/`fetch` | `GitHttpIntegrationTest.pushCloneAndFetchRoundTripThroughARealGitClient`, `UploadPackHandlerTest` |
| FR-XFER-2 (push validated per ref, atomic) | Done | Yes — real `git push` | `GitReceivePackAuthorizerTest`, `GitHttpIntegrationTest.anonymousPushIsRejectedWithAnAuthChallenge`, `ReceivePackHandlerTest` |
| FR-REPO-1 (create repo with visibility) | Done | Yes — `POST /api/repos` (user- or org-owned); UI at `/repos/new` | `OrgAndAccessControllerTest.anOrgAdminCanCreateAnOrgOwnedRepoButAStrangerCannot` |
| FR-REPO-2 (collaborators + team grants) | Done (gap-closure) | Yes — `POST/DELETE .../access/collaborators`, `.../access/teams`; UI at `/{owner}/{repo}/settings/access`, org/team creation at `/orgs/new`, `/orgs/{org}/teams` | `OrgAndAccessControllerTest` (9 cases), `TeamAccessEndToEndTest` (real `git push` by a team member with no direct grant) |
| FR-REPO-3 (effective role = max of grants) | Done | Yes — underlies every authorization check | `DefaultPermissionResolverTest` (8 cases incl. cyclic team, depth cap, "max wins") |
| FR-REPO-4 (protected branch push rejected) | Done | Yes — branch protection UI at settings/access; enforced on real push and PR merge | `GitReceivePackAuthorizerTest`, `PullRequestServiceTest.branchProtectionRequiringApprovalBlocksAnUnapprovedMerge` |
| FR-COLLAB-1 (open issue) | Done | Yes — `POST .../issues`; UI at `/{owner}/{repo}/issues` | `IssueControllerTest` |
| FR-COLLAB-2 (PR proposes merge, lifecycle state) | Done | Yes — `POST .../pulls`, `GET .../pulls/{number}`; UI at `/pull/{n}` | `PullRequestServiceTest`, `PullRequestStateTest`, `PullRequestControllerTest` |
| FR-COLLAB-3 (review, attached to lines) | **Partial** | API and domain: yes (`Review.path`/`line`, `POST .../reviews` accepts them). UI: **no** — `ReviewComposer` only ever submits a body-level review; there is no way for a real user to click a diff line and attach a comment to it, even though `DiffView` renders the diff the frontend spec says should anchor to | No test exercises a review with `path`/`line` populated; the UI path plainly doesn't exist. Named here rather than left implied by the "done" domain model |
| FR-COLLAB-4 (merge commit / squash / rebase) | Done (rebase added, gap-closure) | Yes — `POST .../pulls/{n}/merge`; UI `MergeBox` offers all three | `PullRequestServiceTest.rebaseStrategyReplaysTheSourceCommitOntoAMovedTargetTip`, `.squashStrategyProducesASingleParentCommit`, `RebaseTest` (5 cases) |
| FR-BROWSE-1 (tree/file, syntax highlighting, blame) | Done (highlighting + blame added, gap-closure) | Yes — `GET tree/blob/blame`; blob page with `CodeViewer` (highlight.js) and a Blame toggle | `RepoContentControllerTest.blameAttributesEveryLineToTheSeedCommit`, `BlameTest` (5 cases) |
| FR-BROWSE-2 (commit history + diff) | Done (compare endpoint added, gap-closure) | Yes — `GET commits/{ref}`, `commit/{sha}`, `compare/{base}...{head}`; commit pages + PR Files-changed/Commits tabs | `RepoContentControllerTest.commitDiffShowsAddedFilesForARootCommit`, `.compareReturnsTheDiffAndCommitsBetweenTwoRefs` |
| FR-SEARCH-1 (trigram code search) | Done (added, gap-closure) | Yes — `GET .../search?q=`; search results page with the indexing/too-short/no-results states | `TrigramIndexTest` (7 cases incl. false-positive rejection), `SearchControllerTest` (5 cases incl. masked-private, indexing state) |

**PRD-named items without their own FR number**, also part of the completion gate in
spirit:

| Item | Status | Reachable end to end | Test id |
|---|---|---|---|
| Labels, milestones, assignees (Tier 2) | Done for issues; **not built for pull requests** (a deliberate scope cut, see below) | Yes for issues — `POST/DELETE .../labels`, `.../assignees`, `PUT .../milestone`; `FilterBar` + `SidebarMeta` in the issue UI | `IssueControllerTest` (9 cases) |
| Access-management UI (Tier 3) | Done (gap-closure) | Yes — `/{owner}/{repo}/settings/access` | `OrgAndAccessControllerTest` |
| User/org profile page `/{owner}` (frontend spec route table) | **Not built** | No — the route has no page; `RepoHeader`'s owner breadcrumb links to it and 404s | none |
| SSH transport (security doc, section 2.4) | **Not built** | No — only Git-over-HTTP exists; no SSH server, no public-key registration | none |

## What was added in the gap-closure round

An audit against `docs/01_PRD.md` found that the first build's own `SUMMARY.md`
under-reported its gaps: it implied access management was missing only a UI, when the
org/team/grant REST API didn't exist at all, and several PRD-named features (search,
blame, labels, rebase) had quietly been dropped without a line in `DECISIONS.md`. This
round closed those, prioritized P0 (reachability) → P1 (named PRD features) → P2
(correctness and quality), each committed separately:

- **P0 — org/team/grant API and UI.** `OrgController`, `AccessController`; the
  `/settings/access`, `/orgs/new`, `/orgs/{org}/teams` screens.
- **P0 — account signup, token minting, and CORS.** `AccountController`
  (`PasswordHasher` existed, wired into zero endpoints); a real CORS configuration
  (nothing existed before, silently breaking every browser-based client fetch, not just
  new ones — `MergeBox`/`ReviewComposer`/`CommentComposer` from the first build were
  affected too, never caught because M8's own verification used `curl`).
- **P0 — `git status`.** `Repository.status()` in `cairn-vcs`, the one Tier 1 porcelain
  command missing. Deliberately has no HTTP/UI surface: Cairn's server-side repos are
  bare (no working tree), and `git status` is inherently a working-directory concept a
  real `git` client already answers correctly, unassisted, against any real clone.
- **P1 — trigram code search, blame, syntax highlighting, labels/milestones/assignees.**
  See the FR table above.
- **P2 — REBASE, PR completeness, sessions, virtualization, `open-in-view`.** See the
  FR table and "Decisions" below.

## Design patterns, where they pay rent

- **Strategy:** `ObjectStore` (loose/packed), `DiffStrategy` (Myers), `PermissionResolver`,
  `MergeStrategy` (merge commit/squash/rebase).
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

## Test status

| Module | Tests | Status |
|---|---|---|
| `cairn-vcs` | 92 | passing |
| `cairn-transfer` | 10 | passing |
| `cairn-api` | 85 | passing |
| `web` | none (no automated suite) | `npm run build` succeeds; manually verified end to end against a running backend |

Total: 187 automated JVM tests, all green (`./gradlew clean test`). Several of the most
important ones are real-binary/real-client integration tests, not mocks: `git clone`/
`push`/`fetch`/`merge`/`fsck` against the running embedded server
(`GitHttpIntegrationTest`), a real `git push` by a team member who only holds a team
grant (`TeamAccessEndToEndTest`), and a real signup → login → session cookie →
server-rendered private-repo page proof (verified live; see `LoginControllerTest` for
the automated half).

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
`http://localhost:3000/acme/demo` directly, sign up a new account at `/signup`, or push
your own repo:

```
git remote add origin http://localhost:8080/<owner>/<repo>.git
git push origin main
```

Session cookies are not marked `Secure` by default (`cairn.cookie-secure=false`), so
login works over plain local HTTP; set it to `true` behind real HTTPS.

## Decisions and assumptions

Every judgment call is recorded with its rationale in `DECISIONS.md`, organized by
milestone (and, for the gap-closure round, by work item), newest first. The recurring
theme across the whole project: whenever a claim could be checked against the real
thing (real `git`, a real HTTP round trip, a real browser client, the full test suite
after a risky change), it was, and real bugs were repeatedly only found this way:

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
- **Gap-closure:** no CORS configuration existed at all (broke every browser fetch,
  old and new); `docs/` had gone missing from disk between sessions with no git history
  to recover it from (repopulated from identical root-level copies); a JDK
  `HttpURLConnection` quirk (a POST receiving a 401 with a body throws instead of
  returning the response) fixed by adding a real HTTP client for tests;
  `@EntityGraph`'s default `FETCH` type silently turned already-eager associations
  lazy, caught by the full test suite; `Issue.removeLabel`/`removeAssignee` relied on
  object-identity `Set.remove`, which only worked by accident under
  `open-in-view=true`'s shared session and broke as soon as it was turned off — fixed
  by matching on id, with a new regression test.

`PROGRESS.md` tracks status and test counts as the project was built, including the
gap-closure round; `DECISIONS.md` is the full assumptions-and-rationale log.

## Known gaps and next steps

**What's left after the gap-closure round, most valuable first:**

- **Line-anchored review comments have no UI** (FR-COLLAB-3, found during this
  round's own audit). The API and domain fully support a review's `path`/`line`; the
  frontend's `ReviewComposer` never offers a way to set them. Highest-value remaining
  item: everything else in the flagship PR screen works, this is the one piece of the
  frontend spec's "Files changed" tab interaction (`click a line to attach a comment`)
  that isn't wired up.
- **Pull requests have no labels/milestones/assignees**, only issues do (a deliberate
  cut this round, made explicitly to avoid repeating the exact "API with no UI behind
  it" gap this whole round exists to close, rather than build the API for PRs too and
  run out of time before its UI).
- **Client-side write islands (`MergeBox`, `ReviewComposer`, `CommentComposer`,
  `AccessSettingsPanel`, etc.) still use the localStorage-PAT flow**, not the new
  session+CSRF cookies. Sessions now work end to end for every Server-Component read
  path (the actual gap that mattered: a private repo's owner couldn't see it through
  any server-rendered page before this round); migrating the write islands too is a
  reasonable follow-up, not done here to avoid destabilizing already-verified flows.
- **No `/{owner}` user/org profile page.** Named in the frontend spec's route table,
  never built in either round; `RepoHeader`'s own owner breadcrumb link 404s.
- **No SSH transport** (security doc, section 2.4). Only Git-over-HTTP exists; no SSH
  server, no public-key registration, no `settings/keys` UI. Not part of either round's
  scope; the PRD does not explicitly mark it paper-only, so it is named here as an
  honest gap rather than implied out of scope.
- **`DiffView` virtualizes per file, not as one continuous list across every changed
  file's rows** (a real, deliberate tradeoff from this round's virtualization work, not
  an oversight — see `DiffView`'s own doc comment). A PR with very many small files
  benefits less than one with one very large file.
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
  machinery beyond the Observer wiring itself, which is what M9 was actually testing.
- **Repository sharding (paper-only, HLD topic per PRD).** Repos are independent
  object-storage namespaces (each `owner/name` already maps to its own on-disk
  directory via `RepositoryRegistry`), so they shard naturally by repo id or name
  hash across nodes or object-store buckets. A routing layer (a lookup table or a
  consistent-hash ring) maps `{owner}/{name}` to a shard; the metadata store
  (`Repo.id`) already has a natural shard key sitting right next to the object
  storage it describes.
- **A minimal CI trigger and project boards/wikis/discussions** are out of scope for
  v1 per the PRD's own "out of scope" list (section 10); not attempted.

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
