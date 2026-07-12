# Cairn: Product Requirements Document

> Working codename: **Cairn**, a self-hosted Git code-hosting and collaboration platform.
> Rename freely. This name is only a consistent handle across the four docs.

**Doc 1 of 4** (PRD, Technical Architecture, Security & Access, Frontend Spec)
**Status:** Draft v2
**Owner:** Utkarsh
**Changes in v2:** roadmap re-tiered so smart-clone negotiation, generation numbers, and packfiles move into the core; partial clone and reachability bitmaps are now explicit paper-only stretch; a documentation goal added so every core operation ships with complexity and tradeoff notes.

---

## 1. Summary

Cairn is a self-hosted platform for storing Git repositories and collaborating on code: browsing, review, and issue tracking, in the shape of GitHub or Gitea. The distinguishing decision is that the version-control layer is implemented for real underneath (a content-addressable object store, a commit DAG, packfiles with delta compression, and transfer negotiation), not faked as ORM rows. That single choice is what turns the project from a CRUD clone into a demonstration of low-level design and systems thinking on a domain every reviewer understands instantly.

This is a portfolio and interview artifact first. The reader is meant to open the repo and see clean, deliberate class design across a rich domain, a genuine mini-Git core, and the thing most clones skip: written reasoning about algorithmic complexity and tradeoffs for every operation that matters.

## 2. Problem and motivation

Two problems, one product.

1. **Product problem:** teams need a place to host repositories with access control, review changes before they merge, and track work. Cairn provides that on infrastructure the owner controls.
2. **Portfolio problem:** interview loops (Indian SWE loops in particular) test low-level design directly: OOP class modeling, design patterns, and SOLID. Cairn is broad enough to force real design decisions (State, Strategy, Composite, Observer) and deep enough (packfiles, delta, negotiation, merge) to prove systems ability, and the tradeoff documentation is what separates it from every other clone.

## 3. Goals and non-goals

### Goals
- A runnable Git engine: init, add, commit, branch, checkout, log, diff, three-way merge, backed by a content-addressable object store.
- Packfiles with a simple delta encoding, so storage and reconstruction are real rather than one-file-per-version.
- Smart transfer negotiation, so a clone or fetch sends only the objects the client lacks.
- Generation numbers over the commit graph, so history queries do not walk all of history.
- A permission model with organizations, teams, repositories, visibility levels, and roles, resolved by a clear algorithm.
- A collaboration layer: issues and pull requests with review, comments, labels, and a modeled lifecycle.
- A web interface to browse code, read history, and run reviews.
- **Documented complexity and tradeoffs** for every core operation (object store, ancestry and merge-base, diff, merge, negotiation, permission lookup, search). This is a first-class deliverable, not a footnote.
- An HLD writeup that explains how the design scales to millions of repos even though the build targets a single node.

### Non-goals
- Feature parity with GitHub. No Actions marketplace, no Projects boards, no Discussions, no Wikis in v1.
- Horizontal scale in code. Sharding, replication, and multi-region live in the architecture doc as design, not as implementation.
- Real-money billing, SSO/SAML, or enterprise admin surfaces.

## 4. Personas

- **Repo owner (developer):** creates repositories, pushes code, opens and merges pull requests, browses history.
- **Reviewer (collaborator):** reads diffs, leaves review comments, approves or requests changes.
- **Org admin:** manages teams, assigns roles, sets repository visibility and branch protection.
- **Reader (anonymous or read-only member):** browses public repositories, reads issues, cannot push.

## 5. Scope

Re-tiered in v2. The differentiators (delta, negotiation, generation numbers) are now inside the core, because they are what prove depth. The genuinely optional-optional features are named explicitly so their absence reads as a decision.

### Tier 1: the VCS engine core (the centerpiece)
- Content-addressable object store: blobs, trees, commits, tags, each addressed by SHA of its content.
- **Packfiles with a simple delta encoding**, and loose objects for the working path. Bounded delta-chain depth.
- Refs and `HEAD`, branch creation and switching.
- The commit DAG, with **generation numbers** for fast ancestry and merge-base.
- A **named diff algorithm** (Myers) with the choice documented against histogram and patience.
- Three-way merge with **recursive merge base** for criss-cross history, hunk-level conflict detection, and documented limitations.
- Porcelain over the plumbing: `init`, `add`, `commit`, `branch`, `checkout`, `log`, `diff`, `merge`, `status`.

### Tier 2: transfer and the platform
- Git over HTTP with **smart negotiation** (the want/have exchange) so a real `git clone` and `git push` transfer only missing objects.
- Users, organizations, teams, repositories.
- Visibility (public, private, internal) and roles (read, triage, write, maintain, admin).
- Issues and pull requests with a modeled lifecycle.
- Reviews, comments, labels, milestones, assignees.

### Tier 3: the web interface
- Repository browsing, file view, commit history, diffs.
- Pull request view with conversation, review, and merge box.
- Issue tracking.
- Access-management UI for orgs and teams.
- Code search over a trigram index.

### Paper-only stretch (documented, not built in v1)
These belong in the HLD writeup as design, with a stated reason for deferral:
- **Partial clone / sparse checkout** (blob filtering, promisor remotes). High complexity, deep coupling to the object model, narrow marginal signal once negotiation and packfiles exist.
- **Reachability bitmaps.** A heavier acceleration than generation numbers; describe the idea and when it pays off, build only if time allows.
- Webhooks and an activity feed, and a minimal CI trigger.

## 6. Functional requirements

### 6.1 Version control
- FR-VC-1: The system stores file content as blobs addressed by a hash of their bytes; identical content is stored once.
- FR-VC-2: A tree object records a directory as a set of named entries pointing to blobs or subtrees.
- FR-VC-3: A commit object records a root tree, zero or more parent commits, an author, a committer, a timestamp, and a message.
- FR-VC-4: Refs (branches and tags) are named pointers to commits; `HEAD` points to the current branch.
- FR-VC-5: The system stores objects in packfiles using a bounded-depth delta encoding, and can reconstruct any object from its delta chain.
- FR-VC-6: The system maintains generation numbers over the commit graph and uses them to accelerate ancestry and merge-base queries.
- FR-VC-7: `log` walks the DAG from a ref; `diff` compares two trees using Myers with linear-space refinement.
- FR-VC-8: The system computes the merge base (or multiple merge bases) of two commits and performs a three-way merge, reporting conflicts per hunk.

### 6.2 Transfer
- FR-XFER-1: On fetch or clone, the server computes the set of objects reachable from the requested refs but not from the client's advertised haves, and sends only that set.
- FR-XFER-2: On push, the server accepts a pack, validates authorization for each ref update, then updates refs atomically.

### 6.3 Repositories and access
- FR-REPO-1: A user or organization can create a repository with a visibility level.
- FR-REPO-2: A repository has collaborators and, for org repos, team grants, each carrying a role.
- FR-REPO-3: Effective permission for a principal on a repository is the maximum of all applicable grants.
- FR-REPO-4: Push to a protected branch is rejected unless the principal holds a sufficient role and any protection rules pass.

### 6.4 Collaboration
- FR-COLLAB-1: A user can open an issue on a repository they can read.
- FR-COLLAB-2: A pull request proposes merging a source ref into a target ref and carries a lifecycle state.
- FR-COLLAB-3: A reviewer can submit a review that approves, requests changes, or comments, attached to specific lines.
- FR-COLLAB-4: A pull request can be merged by merge commit, squash, or rebase, subject to access and protection rules.

### 6.5 Browsing and search
- FR-BROWSE-1: A reader can browse the file tree of any ref and view a file with syntax highlighting and blame.
- FR-BROWSE-2: A reader can view commit history and any single commit as a diff.
- FR-SEARCH-1: A reader can search code within accessible repositories, served by a trigram index rather than a scan.

## 7. Representative user stories

- As a developer, I run `git push` to Cairn over HTTP and my commits appear in the web UI history.
- As a developer, I clone a large repo and only the objects I lack come down the wire.
- As a reviewer, I open a pull request, read the diff, comment on line 42, and request changes.
- As an org admin, I add the `backend` team to a repository with the `write` role and everyone on that team can now push.
- As a maintainer, I enable branch protection on `main` so nobody can force-push it.

## 8. Acceptance criteria (MVP gate)

- `git init` through the engine produces a valid object store; `commit` creates reachable objects.
- Objects round-trip through a packfile: pack, then reconstruct via the delta chain, byte-for-byte.
- Two divergent branches with a shared ancestor merge correctly; a conflicting change is reported as a conflict, not silently lost; a criss-cross case resolves via a recursive merge base.
- A real `git clone http://.../repo.git` succeeds against Cairn, and a fetch after new commits transfers only the new objects (verified by counting objects sent).
- Merge-base on a deep history terminates early using generation numbers rather than walking the whole graph (verified by instrumenting the walk).
- A private repository is not readable by a principal without a grant, verified by an automated test.
- A pull request moves through its lifecycle states and cannot be merged by a principal lacking write.
- The web UI renders a repository tree, a file, a commit diff, and a pull request conversation.
- Every core operation in the architecture doc carries a complexity and tradeoff note.

## 9. Milestones (build order)

1. **M1: object store and DAG.** Blobs, trees, commits, refs, `HEAD`. `init`, `add`, `commit`, `log`.
2. **M2: diff and merge.** Myers diff; merge-base including multiple bases; recursive three-way merge; hunk-level conflict reporting. `branch`, `checkout`, `diff`, `merge`.
3. **M3: generation numbers.** Precompute and persist; wire into ancestry and merge-base.
4. **M4: packfiles and delta.** Bounded-depth delta encoding; pack and unpack; reconstruction.
5. **M5: transfer with negotiation.** Git over HTTP; the want/have exchange; send only missing objects.
6. **M6: permissions.** Users, orgs, teams, repos, visibility, roles, resolution algorithm, branch protection.
7. **M7: collaboration.** Issues, pull requests, reviews, comments, lifecycle states, merge strategies.
8. **M8: web UI.** Browsing, diffs, PR view, access-management screens, trigram code search.
9. **M9: HLD writeup and paper-only stretch.** Sharding, partial clone, and reachability bitmaps on paper; optional builds if time allows.

## 10. Out of scope for v1

CI runners and pipelines, Actions-style marketplace, project boards, wikis, discussions, package registry, SSO/SAML, encryption at rest, and horizontal scale in code.

## 11. Success metrics

**Product-shaped (proves it works):**
- A real Git client can clone and push, and fetch transfers only missing objects.
- Objects survive a pack and reconstruct round-trip.
- A conflicting merge is detected rather than corrupting history.
- Access control passes an automated suite of allow and deny cases.

**Portfolio-shaped (proves the point of building it):**
- The architecture doc names the design patterns and the algorithms (Myers, recursive merge base, trigram search) and states the complexity and tradeoff of each core operation.
- The VCS engine is a standalone, testable library module (a JAR) independent of the web layer.
- The HLD writeup answers "how would this scale to millions of repos" convincingly, a common system-design prompt.
