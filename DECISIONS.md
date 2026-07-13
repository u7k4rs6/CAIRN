# Decisions

Judgment calls made while building Cairn, newest first within each milestone.

## Gap-closure round: P2 (correctness and quality)

- **`spring.jpa.open-in-view=false` was verified empirically, not just flipped.**
  Flipping it broke five `IssueControllerTest` cases immediately, in two genuinely
  different ways: (1) `IssueJpaRepository`'s first `@EntityGraph` attempt used the
  default `FETCH` type, which replaces the entire fetch plan for the query, silently
  turning `Issue.repo`/`author` (plain `@ManyToOne`, eager by JPA default) into
  unfetched proxies; fixed by using `EntityGraphType.LOAD`, which only adds the named
  paths (`labels`/`assignees`/`milestone`, the only lazy-by-default associations) and
  leaves every other attribute's own mapped fetch type alone. (2) A real, separate bug
  the flag flip exposed rather than caused: `Issue.removeLabel`/`removeAssignee`
  called `Set.remove(entity)`, relying on default object-identity `equals`. That
  happened to work under `open-in-view=true` (one Hibernate session for the whole
  request, so its first-level cache deduplicated same-row entities across separate
  repository calls) and silently no-ops now that each repository call gets its own
  session. Fixed by matching on `id` instead; a new regression test
  (`removingALabelActuallyPersistsAcrossSeparateRequests`) covers it, since the
  existing assignee-removal test happened to already catch its own version of the bug
  but nothing had covered label removal specifically. After both fixes, every other
  controller was smoke-tested live against the running app (issues, PRs, single-PR,
  activity feed, tree/blob/commits/commit, access management, PR merge, comments,
  org/team creation, search) before considering the flag safe.
- **`DiffView` virtualizes per file, not as one continuous list spanning every changed
  file's rows.** The frontend spec's stated ideal is one virtualized list across the
  whole diff. Per-file virtualization already delivers the real win (a single huge
  file no longer costs O(file length) to render) without the added complexity of a
  heterogeneous virtualized list mixing file-header rows and diff rows across file
  boundaries. Named as a real tradeoff in `DiffView`'s own doc comment: a PR with very
  many small files benefits less than one with one very large file.
- **`react-window` (not a hand-rolled windowing scheme) for `CodeViewer`/`DiffView`.**
  A hand-rolled scroll-position-based slice would duplicate what a small, focused,
  well-known library already does correctly (overscan, resize handling, imperative
  scroll-to-row), for no benefit over adding the dependency.
- **Client-side write islands (`MergeBox`, `ReviewComposer`, `CommentComposer`,
  `AccessSettingsPanel`, `SidebarMeta`, org/repo creation forms) were not migrated to
  session+CSRF cookies.** They continue to use the existing, already-verified
  PAT/localStorage flow. The actual gap that mattered was Server Component reads (a
  private repo's owner could never see it through any server-rendered page, since a
  Server Component runs on the Next.js server and cannot read the browser's
  localStorage); that is fixed for every read path via `lib/api.ts` forwarding the
  session cookie. Fully migrating every write island too is a reasonable follow-up,
  deliberately not done in this pass to avoid destabilizing flows the first build
  already got working and verified.
- **CSRF is the double-submit cookie pattern (a second, readable `cairn_csrf` cookie
  echoed back as a header), not server-side per-request token storage.** Stateless to
  verify (just compare the header to the session's own minted value), and only
  matters for session-cookie-authenticated requests: a personal access token over
  Basic auth is never attached to a request automatically by a browser, so it carries
  no ambient-credential forgery risk for CSRF to defend against.
- **Sessions are in-memory (`SessionStore`), like `InMemoryActivityFeed`.** A restart
  logs everyone out; the same overall scope tradeoff as this project's H2-in-memory
  persistence generally, not a shortcut specific to auth.
- **Added `org.apache.httpcomponents.client5:httpclient5` as a test-only dependency.**
  `TestRestTemplate`'s default JDK-based `HttpURLConnection` throws
  `HttpRetryException` ("cannot retry due to server authentication, in streaming
  mode") on a POST that receives a 401 response with a body, since it unconditionally
  attempts its own auth-retry flow on any 401 regardless of whether the app set a
  `WWW-Authenticate` header. This is a real, previously-latent risk this round's own
  new tests hit (`LoginControllerTest`'s wrong-password case); every prior 401-on-POST
  test in the codebase happened to return an empty body, which doesn't trigger it.
  Spring Boot auto-detects a real HTTP client on the classpath and prefers it over the
  JDK's, which fixes the underlying issue rather than working around it by stripping
  the response body.
- **`REBASE` follows only the first-parent chain and is all-or-nothing on conflict.**
  Matches this project's existing merge/blame scope boundaries (`Blame`, `TreeMerger`
  both make the same call): a merge commit inside the rebased range is flattened into
  one ordinary commit rather than preserved as a merge, and any conflict aborts the
  whole rebase with no partial history written, since there is no working directory on
  the server to pause a real interactive rebase in.
- **The `compare` endpoint's route is `/compare/{base}...{head}` with bare ref names
  (not `refs/heads/...`), matching every other read endpoint's `{ref}` convention.**
  A full ref name contains `/`, which does not fit cleanly into one path segment
  alongside a second path variable; the frontend strips `refs/heads/` before calling
  it.

## Gap-closure round: P1 (named PRD features that had quietly been dropped)

- **Labels/milestones/assignees were built for issues only, not pull requests.** A
  deliberate scope cut, made explicitly to avoid repeating the exact "API with no UI
  behind it" gap this whole round exists to close: building the domain/API for PRs too
  under time pressure and running out of room for its UI would have been the same
  mistake in miniature.
- **Label/milestone mutation endpoints are gated at `triage`, not `write`.** Matches
  the security doc's own definition of the role ("manage issues and PRs without code
  write") and real GitHub's equivalent behavior.
- **Blame follows only the first-parent chain, the same scope boundary as `REBASE`
  above (written earlier, same reasoning): a merge's second-parent history is not
  traced, and a rename is an unrelated delete-and-add**, consistent with
  `TreeMerger`'s own documented rename limitation from M2.
- **Syntax highlighting is per-line, not per-file.** Highlighting the whole file as
  one token stream and splitting the resulting HTML by newline would corrupt whenever
  a token (an unterminated multi-line comment or string) spans a line boundary, since
  an opening `<span>` would not close on the same line it started. Per-line
  highlighting loses that cross-line context in exchange for correctness of the
  line-by-line rendering every other part of the app already assumes (line numbers,
  the blame gutter, review line-anchoring). Named in `lib/highlight.ts`'s own doc
  comment.
- **The trigram index rebuilds off the request thread when the branch tip moves,
  returning an "indexing" state rather than blocking or serving stale results.**
  Neither of the architecture doc's two named options (synchronous rebuild on
  receive-pack, which slows every push; an asynchronous job-worker queue, not built)
  — a third, simpler middle ground that still gives the frontend spec's named
  indexing state real meaning instead of a state that can never actually trigger.
- **Trigram search bounds indexing explicitly: files over 512KB are skipped, and a
  repo stops indexing past 20,000 files.** Named per the security doc's requirement
  that search be bounded, rather than silently truncating with no signal.

## Gap-closure round: P0 (reachability)

- **`git status` deliberately has no HTTP/UI surface.** Cairn's server-side repos are
  bare (no working tree; `RepositoryRegistry`'s own doc comment says so), and `git
  status` is inherently a working-directory concept. A real `git` client already
  answers it correctly, unassisted, against any real clone from Cairn; building a
  redundant server-side surface for a question the client already answers would be
  scope for its own sake.
- **Account signup mints tokens by authenticating with the real password over Basic
  auth, once, specifically for that one endpoint** (`POST /api/users/{username}/tokens`),
  rather than building full session/cookie login as part of this same P0 item. Real
  session login was still a named P2 gap at the time; this was the minimum needed to
  let a signup flow end in a working, usable account rather than a dead end.
- **CORS allows one configurable origin (`cairn.web-origin`, default
  `http://localhost:3000`), not a wildcard**, since credentials (cookies, later) are
  allowed, and a wildcard `Access-Control-Allow-Origin` cannot be combined with
  `Access-Control-Allow-Credentials: true` per the CORS spec itself, not just this
  project's own preference.
- **A repo's "last-admin guard" (frontend spec 5.9) has no teeth to add, and none were
  added.** `Repo.ownerUser`/`ownerOrg` always implies admin and is immutable through
  the access-management API; there is no grant row for the owner to remove in the
  first place, so the UI simply never offers one, which already satisfies the spec's
  intent (never let a repo's admin trail off to zero) without dead defensive code
  that could never actually trigger under this domain model.
- **Team-to-repo grants are managed in `AccessController` (repo-scoped), not
  `OrgController` (org-scoped)**, even though both touch `Team`. A grant is fundamentally
  a fact about the repo (who can push it), not the org; keeping it next to collaborator
  grants (the other grant source) means one controller owns "everything that changes a
  repo's effective-role inputs."
- **Restored `docs/` from the identical root-level copies rather than treating its
  absence as a blocker.** The four spec files existed at the project root the whole
  time (apparently the originals, from before a `docs/` copy was made and later lost
  from disk with no git history to recover it from); confirmed byte-identical before
  proceeding, consistent with investigating unfamiliar state rather than assuming it
  was safe to route around.
- **This round's audit was done by independently reading the code against the PRD,
  not by trusting the first build's own `SUMMARY.md`/`DECISIONS.md`.** That is exactly
  what surfaced the under-reporting itself (access management's real gap was the
  missing write API, not just a missing UI, which the first build's own account of its
  gaps did not say) — a self-report written by the same effort that built the code is
  evidence of intent, not proof of what actually shipped.

## M1: object store and DAG

- **Object hashing uses SHA-1 and Git's exact on-wire encoding, not a modern hash.**
  The PRD's M5 gate requires a real `git clone`/`git push` to work against Cairn.
  That only works if the ids Cairn advertises for refs are the same ids a real git
  client independently recomputes from the objects it receives, which means matching
  Git's canonical object framing (`"<kind> <len>\0<body>"`, SHA-1) exactly, not just
  conceptually. Verified directly: Cairn's blob and tree hashing for known content
  match `git hash-object` and `git write-tree` byte for byte (see `GitObjectTest`).
- **Tree directory mode is the literal string `"40000"`, not `"040000"`.** Same
  reasoning: Git's own tree encoding omits the leading zero, and the encoding feeds
  the hash, so reproducing the quirk exactly is required, not optional polish.
- **The index is a flat, sorted, persisted path-to-blob map**, not a full Git index
  (no stat cache, no merge stages). Cairn does not need `git status`-speed staleness
  detection; simplicity was chosen over matching Git's index format, since nothing
  outside Cairn ever reads this file directly.
- **`log()` walks history with a max-heap ordered by committer time**, not strict
  topological order. Good enough for M1; generation numbers (M3) give a principled
  way to accelerate and order this walk without changing the public API.
- **Gradle toolchain pinned via `sourceCompatibility`/`targetCompatibility = 17`**,
  not a `JavaLanguageVersion` toolchain request, because the sandboxed build
  environment only has a JDK 21 installation and no toolchain auto-provisioning
  network path. The produced bytecode still targets Java 17.

## M2: diff and merge

- **The linear-space Myers middle-snake search was validated by property test, not
  derivation alone.** The forward/backward diagonal correspondence (`k' = delta - k`)
  is easy to get backwards (a sign error compiles fine and mostly "looks" like it
  works on small symmetric inputs); the fix was found by running 800+ randomized
  cases against a brute-force LCS oracle asserting both round-trip correctness and
  minimality, not by re-reading the algorithm more carefully. Kept the oracle test
  in the suite rather than deleting it once green, since it is the real guarantee.
- **`diff` and `merge` treat a tree as a flat set of file paths**, not a recursive
  structure, when comparing two trees. This makes non-overlapping changes in
  different subtrees merge trivially and is the natural fit for the per-path
  three-way decision table; the cost is that a whole-subtree move is seen as N
  unrelated per-file changes, consistent with the architecture doc's stated rename
  limitation (no move detection).
- **`merge` and `diff` each flatten trees independently** rather than sharing one
  flattener, to avoid a package cycle: `repository` (porcelain, which uses `merge`
  and `diff`) must depend on both, so neither `merge` nor `diff` can depend on
  `repository`'s `Index`/`TreeBuilder`. The ~15 lines of duplication is the cheaper
  cost versus an inverted or circular module dependency.
- **Add/add (both sides created the same path with different content) and
  modify/delete are whole-file conflicts**, not routed through the hunk-level
  `FileMerge`. Feeding an empty "base" into the line merger would let two
  unrelated zero-width inserts at position 0 concatenate silently instead of
  conflicting, which is the wrong behavior for "two entirely different files with
  the same name" - worth a conflict, not a silent merge.
- **A conflicted merge still produces and materializes a best-effort merged tree**
  (ours' content wins per conflicting path) but does not create a commit. This
  matches real Git leaving conflict markers in the working tree for the user to
  resolve, rather than aborting with nothing on disk to look at.
- **Recursive merge-base folding beyond two lowest common ancestors reuses the first
  fold's synthesized tree as the base for absorbing further bases**, rather than
  finding a proper recursive base for it (impossible in general, since the folded
  result is a tree, not a commit with its own ancestry). Three-or-more-way
  criss-cross is rare; this keeps the fold simple and terminating. Documented in
  `MergeEngine`'s Javadoc as well, per the architecture doc's own allowance for
  named merge limitations.

## M3: generation numbers

- **Generation numbers live in a separate flat file (`FileGenerationStore`), never
  inside the `Commit` object.** Embedding it would change the commit's hash, which
  would break every cross-check against real `git` from M1 onward. This mirrors
  Git's own commit-graph file being auxiliary, derived data.
- **`mergeBases` keeps M2's exhaustive, proven-correct algorithm as the fallback for
  the general case, rather than replacing it with a generation-bounded frontier
  search.** A frontier search that stops once the remaining queue's generation
  drops below the lowest found candidate's is what real Git approximates, but doing
  it correctly requires stale-marking bookkeeping to rule out a genuinely
  independent, lower-generation, non-dominated common ancestor in an adversarial
  graph; generation number alone does not guarantee ancestry-comparability between
  nodes at different generations. Rather than ship an optimization with a subtle,
  hard-to-test correctness gap, `mergeBases` only fast-paths the case generation
  numbers make unambiguously safe (one side already an ancestor of the other,
  checked in O(1)/pruned time) and falls back to full enumeration otherwise. This
  is the same honest tradeoff the architecture doc itself asks for: a real,
  demonstrable win in the common case, without overclaiming a complexity-class
  improvement in the adversarial one.
- **The "instrument the walk" acceptance criterion is proven via a counting
  `ObjectStore` decorator in the test**, not a hook baked into `Ancestry` itself,
  since every commit visited requires at least one `get()` and adding
  production-code instrumentation for a test-only concern would be its own
  complexity for no runtime benefit.

## M4: packfiles and delta

- **The packfile byte format targets Git's real, wire-compatible pack format**
  (magic, version 2, per-entry type+size header, REF_DELTA, trailing SHA-1), not an
  internal-only format, even though M4 itself only requires an internal round trip.
  Doing this now means M5's transfer layer can serve exactly these bytes to a real
  `git` client without a second implementation. Verified directly, not just assumed:
  Cairn-written packs (including one with a 5-deep REF_DELTA chain) were accepted by
  real `git index-pack --stdin`, passed `git fsck --full` and `git verify-pack -v`
  with the expected chain lengths, and reconstructed correctly via `git cat-file -p`.
- **REF_DELTA only, no OFS_DELTA.** Git supports both; OFS_DELTA (offset-based,
  saving 20 bytes per delta by not repeating the base's id) is Git's own preferred
  encoding for smaller packs, but REF_DELTA is equally valid and universally
  supported by real clients, and is simpler to write and to resolve (no need to
  track byte offsets of prior entries during a streaming write). A real pack size
  optimization left for later if the project revisits transfer efficiency.
- **The delta copy/insert script reuses `MyersDiff` at the byte level** rather than
  a rolling-hash matcher (Git's real approach). This is the same tradeoff named in
  `MyersDiff`'s own Javadoc: correctness confidence (the algorithm is already
  exhaustively tested) over Git's linear-time-regardless-of-similarity guarantee.
  Since delta encoding is only attempted between objects already believed similar
  (same kind, chosen by the base-selection heuristic, and only kept if it actually
  shrinks the object), the edit distance D driving Myers' cost stays small in
  practice.
- **Base selection is "the most recent prior object of the same `ObjectKind`"**,
  an O(1)-per-object heuristic, not Git's similarity-window sort over size and
  content. It works well for the common case (adjacent revisions of the same file
  appear close together in a commit walk) and never makes a pack worse than
  storing everything in full, since a delta is only used when it is smaller.
- **`PackedObjectStore` decodes the whole pack into memory up front** rather than
  lazily resolving one object's delta chain from a byte offset on each `get` (Git's
  real approach, backed by a separate `.idx` file this project does not implement).
  Simpler, and sufficient at the scale this project targets; the tradeoff is an
  O(pack size) load cost paid once at construction instead of spread across calls.

## M5: transfer with negotiation

- **No `multi_ack`/`multi_ack_detailed`/side-band capability is advertised.** The
  client sends its full want/have list in one request and gets back a single `NAK`
  plus the raw packfile, no intermediate round trips and no band-framed response.
  This is a real protocol simplification (chattiness and progress reporting), not a
  shortcut around negotiation: the server still computes and sends exactly
  `reachable_from(wants) \ reachable_from(haves)` from whatever haves it received.
- **`ofs-delta` is deliberately not advertised for receive-pack**, discovered as a
  real risk rather than assumed safe: advertising it would tell a real Git client it
  may push an OFS_DELTA-encoded pack, but `PackReader` only implements REF_DELTA.
  Omitting the capability keeps a real client on REF_DELTA, which we can read.
- **Repository creation is auto-vivify-on-first-access** (`RepositoryRegistry`),
  with no ownership, visibility, or auth check yet. This is explicitly staged: M5 is
  transfer mechanics, M6 is the permission model and real repository creation. Both
  `ReceivePackHandler` and `SecurityConfig` carry Javadoc saying so, so the gap reads
  as a plan, not an oversight.
- **A real end-to-end test (an actual `git` binary against the running embedded
  server) is what actually found a correctness bug** the unit tests could not: this
  environment signs commits by default, and `Commit.parse` was silently dropping
  the `gpgsig` header it didn't recognize, changing the commit's recomputed hash on
  round-trip. Fixed in the object model (M1), not worked around in transfer, since
  the bug was that Cairn's commit model didn't account for Git's extensible-header
  reality, not a protocol issue. Kept the failing real-git test in the suite, plus a
  byte-exact regression test built from the actual captured bytes.
- **The ref advertisement always includes a `HEAD` entry and a `symref=HEAD:...`
  capability**, not just the raw branch refs. Missing this doesn't break the object
  transfer at all (an easy thing to miss and have look "working"): a clone still
  succeeds and fetches everything, but the client has no way to know which branch to
  check out, so the working directory silently comes up empty. Caught by asserting
  the cloned file actually exists on disk, not just that the clone command exited 0.

## M6: permissions

- **Repository (persistence) pattern is Spring Data JPA repositories directly**,
  not a hand-rolled interface plus two implementations. `JpaRepository<T, ID>` already
  is the abstraction the architecture doc asks for ("isolate the domain from the
  database; swap Postgres for in-memory in tests"); the one place that genuinely
  needed a swappable, framework-free abstraction - `PermissionResolver`'s data
  access - gets its own narrow `GrantLookup` interface instead, tested with a plain
  in-memory fake with zero Spring involvement.
- **Personal access tokens are hashed with SHA-256, not Argon2id.** The security
  doc mandates Argon2id for passwords specifically (low-entropy, human-chosen
  secrets that need a slow hash to resist offline guessing); a token is a
  high-entropy value Cairn itself generates, so a fast hash for the lookup is the
  standard, correct choice (matching real-world practice), not a shortcut.
- **Spring Security is on the classpath but not used for method-level
  authorization.** `SecurityConfig` stays `permitAll`; every controller resolves a
  `Principal` itself via `PrincipalResolver` and calls `PermissionResolver.authorize`
  explicitly. This keeps the permission model's own logic (the part meant to be
  legible and testable on its own) front and center rather than folded into Spring
  Security's filter chain and annotation machinery, at the cost of not getting
  Spring Security's declarative `@PreAuthorize` conveniences.
- **Repos auto-vivify as `PUBLIC`** the first time any Git-over-HTTP path touches an
  `owner/name` that doesn't exist yet, mirroring `RepositoryRegistry`'s M5 behavior.
  A real "repo not found" 404 therefore can't actually occur yet in this model; true
  existence-masking (security doc, section 6.3) only bites for repos explicitly
  created `PRIVATE`/`INTERNAL` via `POST /api/repos`, which is what the private-repo
  test exercises. Full repo lifecycle (explicit creation always, no auto-vivify) is
  left to M7/M8's REST surface.
- **Two real bugs surfaced only by driving the permission checks through actual HTTP
  requests with a real git client, not by the unit tests, which all passed on the
  first attempt they were written:**
  1. Git does not send `Authorization: Basic` preemptively from credentials embedded
     in a remote URL; it only retries with them after a `401` with a
     `WWW-Authenticate` challenge. `GitHttpController` was returning a flat `404` for
     every denial, which is correct for "authenticated but insufficient" but starves
     an anonymous client of the chance to authenticate at all. Fixed by branching on
     whether the principal is anonymous.
  2. `RepoJpaRepository.findByOwnerAndName`'s hand-written JPQL used bare path
     expressions (`r.ownerOrg.name`) in an OR condition; JPQL implicitly INNER JOINs
     a path expression's association, so any repo with a null `ownerOrg` (i.e. every
     user-owned repo) was silently excluded from the join before the `is not null`
     guard in the WHERE clause ever got a chance to matter. Fixed with explicit
     `LEFT JOIN`s. Worth naming because the query returned no error, just no rows,
     which is exactly the kind of correctness bug a fast unit test around the
     resolver's logic alone would never see, since that logic doesn't touch JPQL at
     all - only exercising the real persistence layer end to end catches it.

## M7: collaboration

- **The PR and issue lifecycles are enum-based State pattern instances**, not a
  sealed interface of separate classes. Java lets an enum constant override methods
  individually, so `MERGED` genuinely overrides nothing and every action on it falls
  through to the "not legal" base case, which is the same structural guarantee a
  sealed-interface State pattern would give. The enum form stays trivially
  `@Enumerated(STRING)`-persistable, which a sealed interface of stateless instances
  would need extra mapping code to achieve, for no behavioral difference here since
  the states carry no per-instance data.
- **`REBASE` is a named merge strategy in the architecture doc but not implemented.**
  `MERGE_COMMIT` and `SQUASH` both reduce to "compute one merged tree, write one
  commit," which is a direct, low-risk extension of `MergeEngine`. A real rebase
  needs to replay each source commit individually against the moving target,
  re-running a three-way merge per replayed commit and handling a conflict at any
  step, which is materially more machinery for a comparatively narrow additional
  signal at this stage. Named here as a gap, not silently dropped.
- **Branch protection's "no force-push" check and "require approval before merge"
  check share the same `BranchProtectionRule` row but are enforced in two different
  places**: force-push/deletion in `GitReceivePackAuthorizer` (the raw Git push
  path), approval-before-merge in `PullRequestService` (the PR merge path, which
  itself pushes through the same `MergeEngine` but never through `receive-pack`,
  since the server is producing the merge commit itself, not receiving one from a
  client). Both read the same rule row, so there is one source of truth for a
  branch's protection even though two different call paths enforce different
  parts of it.

## M8: web UI

- **Next.js Server Components fetch directly from `cairn-api` on every page load**,
  with no client-side data layer (no SWR/React Query, no API route proxying) for the
  read paths. The frontend spec's masked-404 principle (a private repo and a
  nonexistent one must render identically) is naturally satisfied this way: the
  server component's fetch either succeeds or throws `ApiError`, and every page's
  catch block renders the same `NotFoundState` regardless of which case it was,
  with no separate client-side branch to keep in sync with the server's.
- **Only the write actions (auth token entry, merge, review, comment) are client
  components.** Everything else stays server-rendered, which is both the simpler
  code and the better fit for a Git host's actual traffic pattern (overwhelmingly
  reads: browsing trees, blobs, history, diffs).
- **The PR conversation page has no Files-changed or Commits tab**, unlike the full
  frontend spec's mockup. Both would need a compare-between-two-refs endpoint
  (base ref vs. head ref, not "one commit vs. its first parent," which is what
  `commit/{sha}` gives), which does not exist yet. Named as a scope cut here rather
  than half-built against the wrong endpoint.
- **There is no `GET /api/repos/{owner}/{name}/pulls/{number}` single-resource
  endpoint.** The PR list endpoint already returns every field the detail page
  needs, and the frontend fetches the list and finds the matching number
  (`lib/api.ts`'s `pull()`). Adding a second endpoint that returns the identical
  shape for one row would be pure duplication for a UI this small; worth revisiting
  if the list ever needs to shrink its payload (e.g. omitting body text).
- **`DiffView` renders every line of every changed file, unvirtualized.** The
  frontend spec's stated ideal is O(visible rows) via windowing. For a demo-scale
  repository this is invisible; called out in a code comment as a deliberate scope
  tradeoff rather than a design the project actually endorses at real scale.
- **Auth is a username + personal access token saved to `localStorage` by a small
  `AuthBar` component**, not a real login/session flow (no cookies, no CSRF
  handling, no OAuth). This matches what the backend actually supports end to end
  (Basic auth with a PAT, per M6) rather than building UI for a session model the
  API doesn't have.
- **`spring.jackson.visibility` (field-based serialization) was chosen over adding
  a `getXxx()` accessor to every domain class.** The domain classes' plain,
  no-prefix accessors (`title()`, not `getTitle()`) were a deliberate M6/M7 choice
  to read as domain behavior, not JavaBean boilerplate; changing that convention
  just to satisfy Jackson would have meant carrying two accessor styles side by
  side. A global visibility config fixes the serialization gap without touching any
  domain class's public shape.
- **Considered, then reverted, `spring.jpa.open-in-view: false`** while investigating
  the Jackson fix. Several controllers rely on lazily-loaded JPA associations
  (e.g. a `PullRequest`'s `repo.ownerUser`) being reachable during response
  serialization, after the initial request-handling transaction would otherwise
  have closed; disabling open-in-view without auditing every such access first
  risked trading one bug for a different, less obvious one under real time
  pressure. Left as a known gap (see SUMMARY.md), not silently worked around.
- **`DevDataSeeder` runs only under an explicit `seed` Spring profile**, never in
  tests and never by default in a normal run, so the "porcelain over a demo repo"
  convenience can't accidentally leak into a real deployment or shift what the
  test suite is asserting against.
- **Real bugs were found and fixed only by actually running the browser client
  against a live backend, not by the existing controller tests**, which all
  predated the frontend and therefore never exercised Jackson's real serialization
  path, a real HTTP round trip, or React's own reserved prop names. Every one of
  the Jackson visibility gap, the `passwordHash`/`tokenHash` leak, the empty-repo
  `resolveRef` crash, the `navigateToBlob` intermediate-directory gap, and the
  React `ref`-prop collision was caught this way and is covered by this
  milestone's fixes plus, where the failure was a backend contract issue rather
  than a frontend-only concern, a regression test.

## M9: paper-only stretch and Observer

- **Observer is built as a real, minimal feature (an activity feed), not left
  paper-only like the rest of M9.** Auditing the codebase against the build
  brief's five named patterns (Strategy, State, Composite, Observer, Repository)
  found Observer entirely unimplemented; the architecture doc ties it specifically
  to "webhooks, notifications, activity-feed fan-out." A small, real slice
  (`ActivityListener`/`ActivityPublisher`/`ActivityEvent`, two independent
  listeners, wired into issue/PR creation and PR merge, a read-gated
  `GET .../activity` endpoint) closes the gap cheaply and is directly testable,
  unlike a paper description of a pattern that isn't otherwise present anywhere
  in the code.
- **A listener that throws is caught and logged by `ActivityPublisher`, not
  propagated.** The event being published (a merge, an issue) has already
  succeeded by the time listeners run; a broken notification is a degraded feed,
  not a reason to fail the action that triggered it. This is also exactly the
  failure isolation a real webhook-delivery listener would need (one slow or
  broken subscriber must not block the others or the triggering request).
- **Outbound webhook delivery (real HTTP calls to subscriber URLs, retries,
  signing) is not built**, only the `ActivityListener` seam it would plug into.
  Per the PRD, this is explicitly named paper-only stretch; the activity feed
  demonstrates the same Observer wiring a webhook delivery listener would use,
  without the additional machinery of HTTP delivery, retry/backoff, and
  signature verification that a real implementation needs.
- **The activity feed is in-memory and unbounded across repos (though bounded per
  repo, 200 events), not persisted.** A restart loses history, matching this
  project's overall H2-in-memory persistence scope; a real deployment would back
  this with a table (`ActivityListener` already isolates that swap to one class).
- **Trigram code search (PRD Tier 3 / FR-SEARCH-1) is a documented gap, not
  built.** It was scoped into M8's web interface tier but not implemented; unlike
  partial clone and reachability bitmaps, the PRD does not explicitly mark it
  paper-only, so this is named here as an honest scope cut under time pressure,
  not a spec-sanctioned deferral. The PRD's own complexity-and-tradeoff
  documentation requirement for search (section 10: "O(total code) grep per
  query" vs. "trigram index: near O(intersected postings + candidate verify)") is
  satisfied on paper in SUMMARY.md, same treatment as the explicitly paper-only
  items below.
- **Partial clone/sparse checkout and reachability bitmaps stay paper-only**, per
  the PRD's own explicit deferral. Both are described with their real tradeoffs
  in SUMMARY.md rather than stubbed with dead code that would only assert a false
  sense of coverage.
- **Repository sharding is described on paper only.** The PRD frames it as an HLD
  writeup topic (section on scale), not a v1 deliverable; `RepositoryRegistry`'s
  existing per-repo namespacing (each `owner/name` maps to its own on-disk
  directory) is already the natural unit a routing layer would shard across,
  which is named in SUMMARY.md rather than re-derived from scratch.
