# Cairn: Technical Architecture

**Doc 2 of 4**
**Status:** Draft v3
**Changes in v3:** implementation language set to Java; code samples use Java interfaces, the module layout uses Gradle modules, and the tech stack lists Java libraries (MessageDigest, Deflater and Inflater, nio) with a Spring Boot API. Docs 3 and 4 are unaffected: security is language-agnostic, and the frontend is React regardless.
**Changes in v2:** every core operation now carries a complexity and tradeoff note; the diff and merge sections name their algorithms and state their limits; packfiles with delta and generation numbers are specified as core; smart transfer negotiation is a core flow; a consolidated complexity table (section 10) is added; partial clone and reachability bitmaps are marked paper-only.

This doc carries the low-level design weight. The reason to build Cairn is to show clean class modeling, design patterns, and SOLID across a real domain, a genuine systems core, and explicit reasoning about *why* each structure was chosen, not only *what* it is. Where a data structure or algorithm is picked, its cost and its alternative are stated inline.

---

## 1. Guiding principles

- **Separation of layers.** The VCS engine knows nothing about HTTP, users, or permissions. It is a library. The platform sits on top of it. The web layer sits on top of the platform. Dependencies point inward only.
- **The engine is a standalone library.** It compiles and tests without a database or a web server. This is the clearest statement of dependency inversion and makes the systems work legible on its own.
- **Patterns where they pay rent.** Every pattern is present because the domain forces it. The doc names each and the pressure that justifies it.
- **Complexity is documented, not implied.** Every operation in section 10 lists its time and space cost, the better alternative, and the tradeoff between them. This is a deliberate answer to the "why over what" gap.
- **Model the domain, not the table.** Classes express behavior. Persistence is an adapter behind an interface.

## 2. System context

```
                 +-------------------+
   git client -->|  Git HTTP transfer|---+  (negotiation: want/have)
   (clone/push)  +-------------------+   |
                                         v
   browser ----->+-------------------+   +----> +------------------+
   (web UI)      |   API / app layer |-------->|  VCS engine       |
                 |  (auth, perms,    |         |  (objects, packs, |
                 |   collaboration)  |         |   DAG+gen numbers,|
                 +---------+---------+         |   diff, merge)    |
                           |                   +---------+--------+
              +------------+-----+          +------------+---------+
              | metadata store   |          | object storage       |
              | (users, repos,   |          | (loose + packfiles)  |
              |  issues, PRs)    |          +----------------------+
              +------------------+
                           |
                +----------+----------+   +-------------+
                | cache               |   | search index| (trigram)
                +---------------------+   +-------------+
                           |
                    +------+------+
                    | job workers |
                    +-------------+
```

## 3. Component breakdown

- **VCS engine (`cairn-vcs`):** objects, loose and packed storage with delta, refs, DAG with generation numbers, diff, merge. No knowledge of anything above it.
- **Transfer (`cairn-transfer`):** the Git smart-HTTP protocol including negotiation. Depends on the engine.
- **Platform / API (`cairn-api`):** authentication, the permission model, repositories, issues, pull requests, reviews. Owns the metadata store. Calls into the engine for anything Git-shaped.
- **Web frontend:** the browser client. Talks to the API only (Doc 4).
- **Metadata store:** relational database for platform entities.
- **Object storage:** filesystem or object-store bucket, one namespace per repository.
- **Cache:** rendered file and diff views, repository metadata, sessions.
- **Search index:** trigram index over code.
- **Job workers:** asynchronous work such as webhook delivery and activity fan-out.

## 4. The version-control engine (centerpiece)

### 4.1 Object model

Four object kinds, each addressed by a hash of its serialized bytes (content addressing). Same content hashes to the same id and is stored once.

- **Blob:** raw file content.
- **Tree:** a directory, an ordered set of entries (name, mode, id of a blob or subtree).
- **Commit:** a root tree id, an ordered list of parent commit ids (zero for the first, two or more for a merge), author, committer, timestamp, message.
- **Tag:** an annotated pointer to another object.

```
interface GitObject {
    ObjectKind kind();          // Blob | Tree | Commit | Tag
    ObjectId id();              // hash of serialize()
    byte[] serialize();
}
```

**Complexity note.** Computing `id` requires hashing every byte of the serialized object, so it is O(object size), not O(1). This matters for the storage costs below and is the honest version of "content lookup is O(1)": only the lookup is constant, the hashing and the read are proportional to size.

### 4.2 Storage: loose objects and packfiles

An `ObjectStore` interface hides where and how objects live (Strategy pattern, and the cleanest demonstration of the open/closed principle: adding a backend touches nothing above it):

```
interface ObjectStore {
    ObjectId put(GitObject obj);
    Optional<GitObject> get(ObjectId id);
    boolean has(ObjectId id);
}
```

**Loose objects (`LooseObjectStore`).** One compressed file per object, keyed by id. The working default for freshly written objects.
- `put`: O(n) to hash and zlib-compress and write, where n is object size. Locate is O(1) expected.
- `get`: O(1) to locate + O(n) to read and decompress.
- Space: every version stored in full (compressed), so O(sum of all object sizes). Storing three versions of a file costs three full copies. This is exactly the inefficiency packfiles fix.

**Packfiles with delta (`PackedObjectStore`).** Many objects in one file. Similar objects are stored as a delta against a chosen base object, not in full. An accompanying index maps object id to an offset in the pack.
- **Delta encoding:** an object is stored as a base id plus a copy/insert instruction stream that reconstructs it from the base. Bases are chosen by similarity (in the simple version, group objects of the same path and pick the nearest prior version).
- **Delta chains:** a delta's base can itself be a delta, forming a chain. Chain depth is bounded (a fixed cap, for example 50) so reconstruction stays bounded.
- `get` (packed): O(1) to locate via the index, then O(chain depth x object size) worst case to walk the chain and apply deltas. The depth cap turns "worst case" into a constant multiplier.
- Space: O(base size + sum of delta sizes). For near-identical versions, deltas are tiny, so this is far below the loose cost. This is the space-versus-CPU tradeoff: packfiles trade extra read-time work for large storage savings.

**Why both exist.** Loose objects make writes cheap and simple; packfiles make storage and transfer efficient. A real Git repository holds both, and the boundary between them is a maintenance operation (packing). Because both satisfy `ObjectStore`, the DAG, diff, and merge code above never learns which one it is talking to (Liskov substitution in practice).

### 4.3 Refs and HEAD

- A `RefStore` maps names (`refs/heads/main`, `refs/tags/v1`) to object ids. Read and write are O(1) per ref; listing is O(number of refs).
- `HEAD` is a symbolic ref pointing at the current branch.
- Checkout materializes a tree into the working directory: O(number of entries in the tree).

### 4.4 The DAG, and generation numbers

Commits form a directed acyclic graph via parent links. Ancestry, reachability, and merge-base are graph queries over this structure.

**Naive cost.** Answering "is A an ancestor of B" or "what is the merge base of A and B" by walking is O(V + E) over the reachable history, which on a large repo means walking potentially the entire graph.

**Generation numbers (the acceleration, promoted to core in v2).** Define `gen(commit) = 1 + max(gen(parent))` over its parents, with roots at 1. Precompute in a single topological pass, O(V + E) once, and persist alongside the graph. Then:
- **Negative ancestry in O(1):** if `gen(A) >= gen(B)` and `A != B`, then A cannot be an ancestor of B, answered without any walk.
- **Pruned walks:** when searching from B, any commit with `gen < gen(A)` cannot lead to A, so that branch is cut immediately.

**Honest framing.** Generation numbers do not change the pathological worst case; a walk can still touch many commits. What they buy is early termination and aggressive pruning, which in practice turns "walk all history" into "walk the recent frontier between the two commits," plus constant-time negative answers. State it this way rather than claiming a complexity-class improvement. Reachability bitmaps (paper-only stretch) go further by precomputing reachability sets, at the cost of building and maintaining the bitmap index.

### 4.5 Diff

Diffing two blobs is a longest-common-subsequence problem. The choice of algorithm is a question a reviewer will ask, so it is stated.

- **Algorithm: Myers.** Runs in O(N D) time, where N is the combined length of the two sequences and D is the length of the minimal edit script (the edit distance). This is fast in the common case because D is small when the files are similar. The worst case, when the files are almost entirely different, degrades toward O(N^2).
- **Space:** the naive dynamic-programming table is O(N^2). A linear-space refinement (divide-and-conquer along the middle snake, the Hirschberg idea) brings memory to O(N), at a small constant-factor time cost. Use the linear-space variant so large files do not blow up memory.
- **Alternatives and why not by default:** histogram and patience diff anchor on lines that are unique across both sides, which produces more human-readable diffs and avoids some of Myers' minimal-but-ugly alignments. They are worth offering as an option (Git offers exactly this). Myers is the default because it yields a minimal edit script and is well understood; histogram is the pick when readability of the diff matters more than minimality.

Diff algorithms sit behind a `DiffStrategy` interface, so the choice is swappable per request (Strategy again).

### 4.6 Merge (the hardest correctness problem)

A three-way merge needs a base. Finding the base is where the subtlety lives.

- **Single merge base:** the lowest common ancestor of the two commits in the DAG, found by walking ancestors of both (accelerated by generation numbers, section 4.4).
- **Multiple merge bases (criss-cross history):** when branches have merged across each other, two commits can have more than one lowest common ancestor, so there is no single base. The **recursive merge strategy** handles this: merge the multiple bases together (recursively, since they too may have multiple bases) to synthesize a single virtual base, then perform the three-way merge against it. This is the part that distinguishes a real merge from a toy that assumes one base.
- **Three-way file merge:** for each file, compare base, ours, and theirs. If only one side changed, take that side. If both changed identically, take it. If both changed differently in the same region, emit a conflict for that hunk rather than guessing.
- **Conflict granularity:** detection is at hunk (region) level, not whole-file, so non-overlapping edits on the same file merge cleanly and only the overlapping region conflicts.
- **Rename detection:** if a file is deleted on one side and a similar file added, a naive pass compares deleted-versus-added pairs by content similarity, O(deleted x added) in the simple form (optimizable with content hashing or a similarity index). Rename detection is what lets an edit follow a moved file instead of surfacing as a delete plus an unrelated add.
- **Documented limitations (state these, do not hide them):** the simple version may not detect renames below a similarity threshold, does not do content-move detection within a file, and resolves criss-cross history by the recursive strategy without the full set of heuristics a production merge uses. Naming the limits is itself a signal of understanding.

The result is a `MergeOutcome` of either a clean merged tree or a set of per-hunk conflicts.

## 5. Domain model (platform side)

- **User** has SSH keys, access tokens, memberships.
- **Organization** owns repositories and contains teams.
- **Team** belongs to an org, has members, holds repository grants, and can nest.
- **Repository** has an owner (user or org), a visibility, collaborators, team grants, branch protection rules, issues, and pull requests. It maps to one object-storage namespace and one `cairn-vcs` handle.
- **Issue** has an author, state, labels, assignees, comments.
- **PullRequest** has a source ref, target ref, state, reviews, comments, and a chosen merge strategy.
- **Review** approves, requests changes, or comments, with line-anchored threads.

## 6. Design patterns and low-level design

Each pattern is listed with the pressure that forces it. These patterns are idiomatic in Java, which makes this section the project's clearest and most legible LLD signal for the interview loops you are targeting.

| Pattern | Where | Pressure it relieves |
|---|---|---|
| Strategy | Object storage (loose / packed); diff algorithm (Myers / histogram / patience); merge strategy (merge / squash / rebase); permission resolution; search backend | Interchangeable behaviors behind one interface; add one without touching callers |
| State | PullRequest lifecycle; Issue lifecycle | State-dependent behavior and legal transitions in one place, not scattered `if` chains |
| Composite | Team and org hierarchy; nested trees in the object model | Uniform treatment of leaves and containers |
| Observer | Webhooks, notifications, activity-feed fan-out | Decouple "something happened" from the reactions |
| Command | Git porcelain operations; undoable actions | Encapsulate an operation as an object; enables queuing, logging, replay |
| Factory | Object deserialization (bytes to the right `GitObject`) | Centralize the concrete-type decision |
| Repository (persistence) | Metadata access behind `UserRepo`, `RepoRepo`, `PullRequestRepo` interfaces | Isolate the domain from the database; swap Postgres for in-memory in tests |

### 6.1 State pattern for pull requests
Legal states: `Draft`, `Open`, `ReviewRequested`, `Approved`, `ChangesRequested`, `Merged`, `Closed`. Each is a type that answers which actions are legal and which states are reachable next, so illegal transitions (merging a closed PR) are structurally impossible rather than guarded by validation everywhere.

### 6.2 Strategy pattern for permission resolution
```
interface PermissionResolver {
    Role effectiveRole(Principal principal, Repository repo);
}
```
Direct grants, team grants (walking the team hierarchy), ownership, and visibility feed a resolution returning the maximum applicable role. Complexity and the algorithm are specified in Doc 3; the cost is summarized in section 10.

### 6.3 SOLID mapping
- **S:** engine does version control; API does access and collaboration; transfer does protocol.
- **O:** new storage backend, diff algorithm, merge strategy, or search backend, all added by implementing an interface.
- **L:** `PackedObjectStore` substitutes for `LooseObjectStore` wherever `ObjectStore` is expected.
- **I:** narrow interfaces (`ObjectStore`, `RefStore`, `DiffStrategy`, `PermissionResolver`) over one fat service.
- **D:** the API depends on engine and persistence interfaces, not concretes; tests inject in-memory implementations.

## 7. API design

### 7.1 REST for the platform
- `POST /api/repos`, `GET /api/repos/{owner}/{name}`
- `GET /api/repos/{owner}/{name}/tree/{ref}/{path}`
- `GET /api/repos/{owner}/{name}/commits`, `.../commits/{sha}`
- `POST /api/repos/{owner}/{name}/pulls`, `POST .../pulls/{n}/reviews`, `POST .../pulls/{n}/merge`
- `POST /api/orgs/{org}/teams/{team}/repos/{repo}` (grant a team a role)

### 7.2 Git HTTP transfer with negotiation
- `GET /{repo}.git/info/refs?service=git-upload-pack` advertises refs for fetch.
- `POST /{repo}.git/git-upload-pack` serves fetch and clone, running the negotiation in section 8.
- `POST /{repo}.git/git-receive-pack` accepts push; every ref update is authorized (Doc 3) before it is written.

## 8. Key flows

**Clone / fetch with negotiation (core in v2).** The client advertises the refs it wants and the commit ids it already has (`want` and `have`). The server computes the object set reachable from the wants but not from the haves, packs only that set, and streams it.
```
missing = reachable_from(wants) \ reachable_from(haves)
```
Both reachability computations use the DAG and generation numbers to prune, so the work and the bytes sent are proportional to the difference between client and server, not to the whole repository. This is the entire point of negotiation: a fetch after one new commit sends a handful of objects, not the repo.

**Push.** Client sends a pack; server unpacks or stores it, then for each proposed ref update runs the authorization and branch-protection checks (Doc 3, section 4.3), and only if every update passes does it write refs (atomic). Post-update events fire to workers.

**Open and merge a pull request.** Create PR (state `Open`); reviews move the State machine; on merge the API checks access and protection, calls the engine to produce a merge under the chosen Strategy (with recursive merge base if needed), updates the target ref, closes the PR.

**Permission check.** On any protected action, resolve the principal's effective role via `PermissionResolver` and compare to the action's required role.

## 9. Scale and HLD (design, not v1 code)

- **Repository sharding.** Repos are independent object-storage namespaces, so they shard by repo id across nodes or buckets. A routing layer maps `{owner}/{name}` to a shard.
- **Metadata store.** Relational, hot read paths served from cache, read replicas for scale-out reads.
- **Caching.** Rendered file and diff views, repo metadata, sessions. Invalidate rendered views on ref update.
- **Code search.** Full-text over code is a trigram or inverted index, not a `LIKE` scan (section 10 states the cost). This is how real hosts do it and is a strong thing to be able to explain.
- **Transfer at scale.** Packfiles plus negotiation minimize bytes; large-object handling is a separate concern (mention, do not build).
- **Fan-out.** Feeds and notifications are asynchronous fan-out off an event stream, which is why Observer sits at the domain boundary.
- **Paper-only stretch:** partial clone (blob filtering, promisor remotes) and reachability bitmaps, described here with their tradeoffs and deferred.

## 10. Complexity and tradeoffs (core operations)

This is the section that answers "why," not just "what." Every operation the engine and platform depend on, with its cost, the better option, and the tradeoff.

| Operation | Baseline cost | Better option | The tradeoff |
|---|---|---|---|
| Object `put` (loose) | O(n): hash + compress + write; O(1) locate | pack later with delta | write simplicity now vs storage cost until packed |
| Object `get` (loose) | O(1) locate + O(n) read/decompress | packed read | fast simple read vs disk footprint |
| Object `get` (packed) | O(1) locate + O(depth x n) to apply delta chain | bound the chain depth (cap ~50) | reconstruction CPU vs storage saved |
| Storage of k versions | O(sum of full sizes), loose | O(base + deltas), packed | space vs read-time delta application |
| Ref read/write | O(1) per ref; list is O(refs) | already optimal | none |
| Ancestry "A ancestor of B" | O(V + E) walk | generation numbers: O(1) negative, pruned walk otherwise | one-time O(V+E) precompute + storage of gen numbers |
| Merge-base | O(V + E) walk | gen-number-pruned walk of the frontier | same precompute; still worst-case linear in adversarial graphs |
| Diff (two blobs) | O(N^2) LCS table | Myers O(N D); O(N) memory via linear-space refinement | speed and memory vs, for histogram/patience, diff readability |
| Merge | three-way per hunk; +recursive cost for multiple bases | rename detection O(deleted x added), optimizable | correctness and rename following vs added computation |
| Fetch / clone | O(whole repo) if you send everything | negotiation: O(size of the missing set) | a negotiation round trip vs bytes saved |
| Permission lookup | O(direct grants + nested team edges walked) | O(1) amortized with a resolved-role cache | recompute simplicity vs cache-invalidation complexity |
| Code search | O(total code) grep per query | trigram index: query near O(intersected postings + candidate verify) | index build time and space vs query latency |

## 11. Recommended tech stack

- **Language:** Java (17 or newer). Fits the LLD and HLD goal directly, since patterns and SOLID read most naturally in Java, which is what the target loops grade, and it reuses your existing Java LLD groundwork.
- **VCS engine and transfer:** plain Java, packaged as a standalone library module (a JAR) with no dependency on the web layer. Hashing via `java.security.MessageDigest`; compression via `java.util.zip.Deflater` and `Inflater`; pack and file I/O via `java.nio` (`FileChannel`, `ByteBuffer`, memory-mapped reads for pack access).
- **Platform / API:** Spring Boot for the REST surface and the Git HTTP endpoints, or a lighter framework (Javalin or Spark) if you want less magic. Either depends on the engine module through its public interface only.
- **Build:** Gradle (or Maven) multi-module.
- **Metadata store:** PostgreSQL, accessed behind the persistence interfaces via JDBC or JOOQ for control, or JPA if you prefer, keeping the domain free of the ORM (Repository pattern).
- **Object storage:** local filesystem in v1, object-store bucket at scale.
- **Frontend:** React / Next.js (Doc 4), independent of the backend language.
- **Search:** an embedded trigram index in Java before reaching for an external search service.
- **Honest note:** Java handles the byte-level packfile and delta work fine with nio and mapped buffers, but it is less of a systems statement than a native language. That is consistent with treating the deep systems features as the optional tier and leaning the project into its LLD and HLD strength, where Java is strongest.

## 12. Repository and module structure

```
cairn/
  cairn-vcs/         # objects, packs+delta, refs, DAG+gen numbers, diff, merge (library JAR)
  cairn-transfer/    # git smart-HTTP protocol incl. negotiation
  cairn-api/         # Spring Boot app: auth, permissions, repos, issues, PRs
  web/               # frontend app
  docs/              # these four documents
  settings.gradle    # binds the modules
```

Dependency rule, enforced by the Gradle module graph: `cairn-vcs` depends on nothing in this repo, `cairn-transfer` and `cairn-api` depend on `cairn-vcs`, `web` depends on the API's HTTP surface only.
