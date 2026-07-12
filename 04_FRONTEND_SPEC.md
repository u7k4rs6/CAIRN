# Cairn: Frontend Spec

**Doc 4 of 4**
**Status:** Draft v2
**Changes in v2:** a rendering-complexity rationale added for the diff and code viewers (the "why over what" ethos applied to the frontend); the search UI aligned to the trigram-indexed backend, including an indexing state.

This spec describes the web interface: the route map, the core screens, the reusable components, and the states each screen must handle. It talks to the API from Doc 2 only and never reaches into the engine or database directly.

---

## 1. Principles

- **Read-first.** Most traffic is browsing code and reading diffs, so those screens get the most polish and the fastest perceived load.
- **The diff is the product.** Pull request and commit views live or die on a legible, fast diff. Treat the diff component as a first-class, heavily reused unit.
- **Render cost is bounded, not file-sized.** Code and diff views virtualize, so rendering cost is proportional to what is on screen, not to the length of the file (section 5.3). This is the frontend version of the complexity discipline the backend docs follow.
- **States are designed, not afterthoughts.** Every screen defines its loading, empty, error, and permission-denied states up front.
- **Access is visible.** Whether the current user can push, merge, or manage access is reflected in the UI, driven by the effective role the API returns.

## 2. Stack
- **Framework:** React with Next.js (App Router); file-based routing maps to the route table below.
- **Data:** a typed API client with a query cache (fetch state, invalidation on mutation).
- **Diff and code rendering:** a virtualized list for large files and diffs; syntax highlighting; line anchoring for review comments.
- **Styling:** a small design-token system (section 9); no heavy component-library dependency required.

## 3. Route map
```
/                                     dashboard (feed, your repos)
/login  /signup                       auth
/{owner}                              user or org profile
/{owner}/{repo}                       repo home (default branch tree + README)
/{owner}/{repo}/tree/{ref}/{path*}    file tree at a ref
/{owner}/{repo}/blob/{ref}/{path*}    single file view
/{owner}/{repo}/commits/{ref}         commit history
/{owner}/{repo}/commit/{sha}          single commit (diff)
/{owner}/{repo}/compare/{base}...{head}   compare two refs
/{owner}/{repo}/branches              branch list
/{owner}/{repo}/pulls                 pull request list
/{owner}/{repo}/pull/{n}              pull request detail
/{owner}/{repo}/issues                issue list
/{owner}/{repo}/issues/{n}            issue detail
/{owner}/{repo}/search?q=             code search results
/{owner}/{repo}/settings              repo settings (incl. branch protection)
/{owner}/{repo}/settings/access       collaborators and team grants
/orgs/{org}/teams                     team management
/settings/keys                        SSH keys and access tokens
```

## 4. Global layout and navigation
- **Top bar:** logo, global search, create menu (new repo, new issue), current-user menu.
- **Repo header (all repo routes):** owner/repo breadcrumb, visibility badge, tab bar (Code, Issues, Pull requests, Settings). Settings appears only when the user can manage the repo.
- **Ref switcher:** a branch/tag dropdown on all code-browsing routes, controlling the `{ref}` segment.

## 5. Core screens
For each: purpose, key components, required states.

### 5.1 Repo home
- **Purpose:** orient the visitor; default-branch tree and rendered README.
- **Components:** `RefSwitcher`, `FileTree`, `ReadmeViewer`, `CloneButton` (HTTP and SSH URLs), `AboutPanel`.
- **States:** loading (skeleton tree), empty repo (quick-start with clone/push commands), private-and-not-permitted (not-found, never a message that confirms existence).

### 5.2 File tree
- **Purpose:** navigate directories at a ref.
- **Components:** `Breadcrumbs`, `FileTree` rows (name, last commit message and time), `RefSwitcher`.
- **States:** loading, empty directory, path not found.

### 5.3 File view (blob)
- **Purpose:** read one file.
- **Components:** `CodeViewer` (syntax highlight, line numbers, virtualized), `BlameToggle`, `FileActions` (raw, history, permalink to the pinned sha).
- **Rendering note:** `CodeViewer` virtualizes rows, so a 50k-line file renders only the visible window plus a small overscan. Cost is O(visible rows), not O(file length). This is why a large file opens instantly instead of freezing the tab.
- **States:** loading, binary file (offer download), file too large (offer raw), not found.

### 5.4 Commit history and commit detail
- **Purpose:** see how the code got here.
- **Components:** history is a list of `CommitRow`; detail reuses `DiffView` plus commit metadata and parent links.
- **States:** loading, empty history, merge commit (show combined and per-parent diff), not found.

### 5.5 Compare
- **Purpose:** the difference between two refs, the precursor to a PR.
- **Components:** two `RefSwitcher`s (`base`, `head`), `DiffView`, a "Create pull request" action when there are changes and the user can open one.
- **States:** identical refs (empty diff, no PR action), large diff (collapse files, paginate).

### 5.6 Pull request detail (the flagship screen)
- **Purpose:** review and merge a change; exercises the most components.
- **Layout:** three tabs.
  - **Conversation:** `PrHeader` (title, state badge, source into target), `Timeline` (comments, review events, state transitions), `CommentComposer`, `MergeBox`.
  - **Files changed:** `DiffView` with inline `ReviewComment` threads anchored to lines, and a `ReviewComposer` (approve / request changes / comment).
  - **Commits:** list of commits in the PR.
- **DiffView rendering note:** the diff virtualizes at the row level across all changed files, so a large PR scrolls smoothly; only visible hunks mount. Files can collapse so an enormous diff does not force-render everything.
- **MergeBox behavior:** reflects mergeability and permission. Shows the available strategies (merge / squash / rebase) only when the user can merge and protection passes; otherwise shows why the merge is blocked (needs approval, conflicts, insufficient role). Conflicts surface which files and, where possible, which hunks.
- **State badge:** renders the PullRequest state from Doc 2 (Draft, Open, Review requested, Approved, Changes requested, Merged, Closed).
- **States:** loading, conflicts present, merged (lock the merge box, show the result), permission-limited (review controls hidden when the role is insufficient).

### 5.7 Issue list and detail
- **Purpose:** track work.
- **Components:** list has `FilterBar` (open/closed, label, assignee) and `IssueRow`; detail has `IssueHeader`, `Timeline`, `CommentComposer`, `SidebarMeta` (labels, assignees, milestone).
- **States:** loading, empty (create prompt), triage-only user (controls reflect the role).

### 5.8 Code search results
- **Purpose:** find code across accessible repos.
- **Components:** `SearchInput`, `ResultGroup` per file with matched-line snippets and highlights.
- **Backend note:** results come from the trigram index (Doc 2, section 10), so queries are fast and scoped to repositories the user can read. A repository whose index is still building shows an **indexing** state rather than incomplete results.
- **States:** loading, no results, query too short (trigram needs at least three characters), indexing, results restricted to readable repos.

### 5.9 Access management (settings/access)
- **Purpose:** the visible face of the permission model.
- **Components:** `CollaboratorList` (user, role, remove), `TeamGrantList` (team, role), an `AddGrant` control with a `RolePicker`, a `VisibilityControl`, and a `BranchProtectionForm` under settings.
- **States:** loading, admin-only (the whole screen is gated on `admin`; non-admins never reach it), last-admin guard (cannot remove the final admin).

### 5.10 Auth and key management
- **Auth screens:** login and signup with clear validation.
- **settings/keys:** `SshKeyList` (add public key, fingerprint, remove) and `TokenList` (create scoped token, show plaintext once, revoke).

## 6. Component inventory
`RefSwitcher`, `FileTree`, `Breadcrumbs`, `CodeViewer`, `DiffView`, `CommitRow`, `Timeline`, `CommentComposer`, `ReviewComposer`, `ReviewComment`, `MergeBox`, `StateBadge`, `RolePicker`, `FilterBar`, `SidebarMeta`, `CloneButton`. `DiffView` and `CodeViewer` are the highest-value components and should be built and tested first, virtualization included from the start.

## 7. Interaction flows
- **Open a PR:** compare two refs, Create pull request, fill title and body, submit; route to the new PR.
- **Review:** on Files changed, click a line to attach a comment, add more, submit a review as approve / request changes / comment; timeline and state badge update.
- **Merge:** in the MergeBox, choose a strategy, confirm; on success the box locks and the PR shows merged.
- **Grant access:** on settings/access, pick a user or team and a role, add it; the list updates and that principal's effective role changes immediately.

## 8. State conventions
Every data view implements four states with shared components:
- **Loading:** skeletons matching the final layout, not spinners, to avoid layout shift.
- **Empty:** a purposeful prompt, not a blank panel.
- **Error:** a retry affordance and a plain message; never a raw stack trace.
- **Denied:** private resources render as not-found so existence is not confirmed (matches Doc 3, section 6.3).

## 9. Design system and visual language
- **Tokens:** a small set of color, spacing, radius, and type tokens; two themes (light and dark) from the same tokens.
- **Type:** a monospace face for code, blame, and shas; a clean sans for chrome and prose.
- **Density:** developer-tool density; compact, scannable lists; the diff maximizes vertical space for code.
- **Aesthetic direction:** align to your brutalist-editorial portfolio language (strong type, restrained color, visible structure) or stay a neutral, GitHub-adjacent dev-tool look. Either works; pick one and hold it across every screen.

## 10. Accessibility and responsiveness
- Keyboard navigation for the file tree, diff, and PR review; visible focus states.
- Semantic structure and labels on interactive controls; sufficient contrast in both themes.
- Responsive to tablet width; code and diff views prioritize horizontal space and allow horizontal scroll rather than wrapping code lines by default.
