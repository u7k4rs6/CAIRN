# Cairn: scaling to millions of repos and users

This is the scale narrative the architecture doc's section 9 pointed at. It describes
the bottlenecks that are actually visible in this codebase today, a sharding strategy
grounded in a key that already exists in the code, and the read/write path under
load. The final section is explicitly a design exercise, not a status report: it
covers two features that are **not implemented** and says so plainly.

Cross-reference: `docs/COMPLEXITY.md` has the per-operation cost each claim below
depends on.

## What this deployment looks like today (the honest baseline)

Everything in Cairn currently runs as **one JVM process against one filesystem
directory and one relational database.** This isn't a simplification for the
narrative — it's what the code does:

- `dev.cairn.api.git.RepositoryRegistry` (`cairn-api/src/main/java/dev/cairn/api/git/RepositoryRegistry.java:34`)
  resolves every `{owner}/{repo}` to a subdirectory of one configured `cairn.repos-dir`,
  and caches the resulting handles in a single in-process `ConcurrentHashMap` (line 32).
  There is no remote storage backend, no per-shard routing, and no second node — the
  registry *is* the entire storage topology.
- Every repository's object store is a `LooseObjectStore` (line 51) — not a
  `PackedObjectStore`. Packing (`PackWriter`) only happens transiently, to build a
  response for a `git fetch`/`clone`; nothing persists a `.pack` file back to disk.
  That means every fetch re-walks and re-deltas objects from loose storage on the
  fly, every time, for every client — there is no cached pack to serve twice.
- The code-search index (`dev.cairn.api.search.RepoSearchIndexService`) is a plain
  `ConcurrentHashMap<String, RepoIndexState>` (line 53) living in the same JVM's heap,
  one full-content `TrigramIndex` per active repo, rebuilt from scratch on every
  branch-tip move.
- All relational data — `User`, `Organization`, `Team`, `TeamMembership`,
  `TeamGrant`, `CollaboratorGrant`, `PersonalAccessToken`, `BranchProtectionRule`,
  `Repo`, `Issue`, `Comment`, `Label`, `Milestone`, `PullRequest`, `Review` — is one
  JPA schema behind one datasource (H2 in tests/dev; nothing in the code assumes or
  configures multiple datasources or a routing layer).

None of this is a defect at the current scale (a portfolio-sized single-node
deployment); it is the reason a scaling story has to start from "what does this
architecture assume that stops being true," not from a generic distributed-systems
essay.

## The three real bottlenecks

### 1. Object store: one filesystem, no pack cache

The object store bottleneck isn't the per-object cost (`COMPLEXITY.md`'s "object
read/write" rows are all O(n) in object size, which is fine) — it's that
**everything shares one directory tree and one disk.** At repo-count scale, this
fails in the ordinary "ran out of inodes / IOPS on one volume" way, well before any
algorithmic limit in `LooseObjectStore` itself is reached. The second-order cost is
CPU, not storage: because nothing persists a pack, `UploadPackHandler.buildResponse`
(`cairn-transfer/src/main/java/dev/cairn/transfer/UploadPackHandler.java:70`) pays the
full `ObjectClosure` walk plus `PackWriter.writePack`'s delta search on *every* fetch
of a popular branch, not once per push. A hot repo (many clones of an unchanging
`main`) redoes the same delta search every single request.

### 2. Search index: in-memory, per-process, per-repo full-content

`RepoSearchIndexService`'s index is a heap-resident structure with no persistence and
no cross-process sharing. Two consequences that are direct reads of the code, not
speculation: (a) memory is O(total indexed content across every repo an instance has
touched recently) — bounded per-repo by `MAX_FILE_BYTES`/`MAX_FILES`
(`RepoSearchIndexService.java:44-45`), but unbounded across repos, since nothing
evicts a cold repo's index; (b) a second instance (for horizontal scaling or just a
restart) has a cold cache and must fully rebuild before it can serve search for any
repo it hasn't seen yet, during which `search()`'s own documented "indexing" transient
state is what a user actually sees.

### 3. Relational metadata: one schema, and it is the wrong join for two real workloads

The JPA layer is a single schema with foreign keys like `Issue.repo`, `Comment.issue`,
`PullRequest.repo` — fine for "show me issue #42 in repo X," expensive for two
workloads this schema was not built to serve at scale: a user's cross-repo dashboard
(every PR/issue assigned to me, across every repo I can see) and an org-wide view
(every open PR across every repo in this org). Both require scanning across the
partition key any repo-based sharding would choose (see below), which is the
textbook "your sharding key isn't your query key" tension — worth naming here rather
than glossing over, since it directly conflicts with the sharding strategy that is
otherwise the right call for the object store and search index.

## Sharding strategy

**Shard by repository — specifically by the `{owner}/{repo}` key `RepositoryRegistry`
already uses as its cache key.** This is not a new abstraction invented for this
document: `RepositoryRegistry.resolve` (line 41) already treats each repo as an
independent, self-contained unit with its own object store, ref store, and
generation store, and `RepoSearchIndexService` already keys its index cache the same
way. Nothing in the object store or search layer reaches across repos — no method in
`LooseObjectStore`, `FileRefStore`, `PackWriter`, `PackReader`, `ObjectClosure`, or
`TrigramIndex` takes a second repo's data as an input. That absence of cross-repo
coupling in the object/search layer is exactly what makes repo-id a viable shard key:
routing "which shard owns repo X" only needs to be resolved once, at the top of a
request, and everything below that point (object reads, packing, search) already
only ever touches one repo's data.

Concretely: replace `RepositoryRegistry`'s single `baseDir` with a routing function
`{owner}/{repo} -> shard`, deterministic (consistent hashing over the repo key, so
resharding moves a bounded fraction of repos) so that a request for a given repo
always lands on the same shard's filesystem and search-index process. Each shard
carries its own object storage, its own `RepoSearchIndexService` instance, and — this
is the part that actually requires new code, not just infrastructure — its own slice
of the relational schema for the tables that are naturally repo-scoped (`Issue`,
`Comment`, `Label`, `Milestone`, `PullRequest`, `Review`, `BranchProtectionRule` all
have a direct `Repo` foreign key and no query in the current code joins across two
different repos' rows of these tables).

**What breaks at the shard boundary:**

- **Anything keyed by user or org instead of repo.** `User`, `Organization`, `Team`,
  `TeamMembership`, `TeamGrant`, `CollaboratorGrant`, `PersonalAccessToken` are
  identity and access-control data, not repo data — they must live in a separate,
  non-repo-sharded (or globally replicated) store, because a permission check
  (`DefaultPermissionResolver.effectiveRole`) needs a user's team memberships
  regardless of which shard the repo being checked lives on. This is a second,
  distinct partitioning problem layered on top of repo-sharding, not solved by it.
- **The cross-repo dashboard and org-wide view named above.** "Every issue assigned
  to me" now means fanning a query out to every shard that holds a repo the user has
  access to, then merging results in the application layer — the query is no longer
  a single indexed lookup, it is a scatter-gather.
- **Global code search** (search across every repo, not one). Today's search is
  already per-repo only (`RepoSearchIndexService` never merges indexes across repos),
  so this isn't a regression *introduced* by sharding — but it does mean "add global
  search" and "add repo sharding" are the same problem: both need a
  cross-shard fan-out-and-merge layer that does not exist yet in any form.
- **Cross-repo pull requests (forks).** `PullRequest` (`cairn-api/src/main/java/dev/cairn/api/collab/PullRequest.java:23-24`)
  holds exactly one `Repo` and two ref names (`sourceRef`/`targetRef`) resolved
  within that same repo — there is no head-repo/base-repo distinction anywhere in the
  entity or in `TreeMerger`/`FileMerge`. Forking is out of scope today, so this
  boundary case doesn't bite yet, but it is the first thing that would: a fork PR's
  three-way merge would need to read objects from two shards in the same operation,
  which nothing in `TreeMerger.merge`, `Ancestry.mergeBases`, or `ObjectClosure.from`
  is built to do (they all assume a single `ObjectStore`).

## Read/write path under load

**Write path (push):** client negotiates via `UploadPackHandler`'s counterpart on the
receive side, uploads a pack, the server decodes it (`PackReader.read`, O(pack size))
and writes each object into that repo's `LooseObjectStore` (O(object size) per
object, per `COMPLEXITY.md`), then updates the ref (`FileRefStore.update`, O(1)) and
recomputes generation numbers for the new commits (`GenerationNumbers.computeAndStore`,
O(1) amortized per commit). Under sharding, all of this stays entirely within one
shard — a push to repo X never touches another repo's storage or another shard's
process, which is exactly the property that makes repo-sharding low-risk for the
write path specifically. The one write-path step that becomes cross-cutting is
**tip-move-triggered search reindexing** (`RepoSearchIndexService`): today this is
in-process and synchronous-ish (an "indexing" state is returned until the rebuild
finishes); at scale this needs to become an asynchronous job queued to the shard that
owns the repo, not inline with the push response, or every push to a large repo makes
that push latency-bound by a full re-walk of the tree.

**Read path (fetch/clone, and API reads):** a fetch is dominated by
`ObjectClosure.from`'s reachability walk plus `PackWriter`'s delta search — both O(size
of what the client is missing) in theory, but as section "The three real
bottlenecks" states, paid in full on every request today since nothing caches the
resulting pack. The direct, low-risk fix that doesn't require the sharding work at
all: cache the built pack per (repo, ref-tip, haves-fingerprint) so a popular
unchanging branch is packed once and served many times — this is a caching layer
change, not an architecture change, and it should happen before or independently of
sharding. API reads (issue lists, PR views) go through the relational schema and, per
the bottleneck above, are cheap today only because they're single-repo scoped; the
cross-repo dashboard queries are the ones that need the fan-out-and-merge layer
regardless of sharding.

## Designed, not built

**Everything in this section is a design sketch, not implemented code. No file in
this repository builds partial clone, sparse checkout, or reachability bitmaps. This
section exists to answer "how would it hook in," not to claim it exists.**

### Partial clone / sparse checkout

**What it is:** letting a client fetch only the commit/tree graph (or only a
directory subset of it) without every blob, fetching blob contents lazily on demand
from a "promisor remote" as the working tree actually needs them (Git's real
`--filter=blob:none` / sparse-checkout mechanism).

**Where it would hook in:** `ObjectClosure.from` (`cairn-transfer/src/main/java/dev/cairn/transfer/ObjectClosure.java:29`)
is the one place that currently computes "everything reachable" — it would need a
filter parameter (a path predicate for sparse checkout, or a "commits and trees only,
no blobs" flag for `blob:none`) threaded through `:walkTree` (line 45) so it stops
descending into excluded paths or stops adding blob ids at all. `UploadPackHandler`
would need to advertise filter support during negotiation and accept a filter spec
from the client alongside `wants`/`haves`. The server side would also need to track,
per client, which objects it promised to serve later on demand — a new piece of
state this codebase has nothing resembling today, since `PackWriter`/`PackReader`
both assume a pack is a complete, self-sufficient unit (no thin-pack / promisor-remote
support, already named as a limitation in `PackReader`'s Javadoc).

**Which complexity bound it would change:** the "transfer negotiation" row in
`COMPLEXITY.md` states negotiation cost is O(size of the full reachable closure of
`wants` and `haves`), not the missing set. A blob-excluding filter would shrink both
`ObjectClosure.from`'s output and `PackWriter`'s work to O(commits + trees) for the
excluded blobs — the closure computation itself would stop being proportional to
total repository *content* size and become proportional to history *shape* (commit
and tree count) alone. It does not change the underlying commit-graph walk itself
(`RevWalk.history`, O(V log V + E) per `COMPLEXITY.md`'s "DAG walk" row — the ordered
walk `ObjectClosure.from` deliberately uses for pack-quality reasons, not the cheaper
unordered O(V+E) `reachableFrom`), which partial clone doesn't touch.

### Reachability bitmaps

**What it is:** precomputed bitmaps (one bit per object, for a chosen set of
"landmark" commits) recording exactly which objects are reachable from that commit,
so that a reachability or closure query can be answered with bitwise OR/AND across
a handful of precomputed bitmaps instead of a fresh graph walk (Git's real
`.bitmap` file, built by `git repack -b`).

**Where it would hook in:** this would replace the live walk in `RevWalk.reachableFrom`
(`cairn-vcs/src/main/java/dev/cairn/vcs/dag/RevWalk.java:66`) for commits at or near a
landmark, and correspondingly in `ObjectClosure.from` and `Ancestry.mergeBases`'s
fallback path, wherever they currently call `reachableFrom`. It would need a new
build step (analogous to `GenerationNumbers.recomputeAll`, but producing a bitmap
store rather than a scalar-per-commit store) run over some chosen set of landmark
commits — most naturally the tip of each ref, recomputed on push, mirroring how
`GenerationNumbers.computeAndStore` already runs incrementally on new commits today.

**Which complexity bound it would change:** the "DAG walk" and "merge-base" rows in
`COMPLEXITY.md` state O(V+E) for a `reachableFrom` walk, and note that generation
numbers do **not** improve that worst case for the general merge-base fallback path
or its O(C²) domination filter. Bitmaps would be the thing that actually does change
that bound: a reachability query against (or near) a landmark commit becomes O(bitmap
size) — proportional to the total object count once, not the graph size walked live
per query — trading a large, periodically-recomputed precomputation cost for cheap
repeated queries. This is a genuinely different lever than generation numbers, which
only prune a live walk; bitmaps replace the walk with a lookup, at landmark commits.
