# Cairn: Frontend Redesign Spec

**Doc: frontend redesign (design system + screens). Status: Draft v1.**

This is a redesign spec, not a build-from-scratch spec. A working `web/` already exists
and covers nearly every screen. This document defines a fully-distinct visual identity
and applies it to that existing app. The data layer stays; the skin changes.

---

## 1. Scope

**In scope:** a new visual identity (tokens, type, layout, signature) applied to the
existing `web/` Next.js app, with the four recruiter-path screens taken to flagship
quality and everything else to clean quality. Plus one net-new page: a minimal
`/{owner}` profile, because the repo header already links to it and currently 404s.

**Out of scope:** any change to `cairn-vcs`, `cairn-transfer`, `cairn-api`, the database,
routing, data-fetching, the Server-Component/client-island split, or any test. No new
platform features beyond the `/{owner}` page. The three remaining known gaps (PR
labels/milestones/assignees, SSH transport, and the FR-COLLAB-3 line-comment UI) stay
out unless explicitly pulled in.

**Why this is worth doing:** the one remaining high-leverage move for this project is a
live URL a recruiter can open. A live URL to a function-first, wireframe-looking UI
undersells a from-scratch VCS engine. A designed one sells it. This pass is the
prerequisite for that, not a detour.

---

## 2. What already exists (do not rebuild)

`web/` renders: repo tree, file view (syntax highlighting, blame), commit diff,
PR conversation with Files-changed and Commits tabs, code search, issues with
labels/milestones/assignees, and the org/team/grant access screens. It uses Server
Components for read paths (so a signed-in session is honored on first render) and
client islands for write paths (login, PR merge, reviews, comments). It builds and
typechecks. Components of note: `CodeViewer`, `DiffView` (both virtualized),
`MergeBox`, `ReviewComposer`. Preserve the fetching, routing, and virtualization.
Rebuild only the presentation.

---

## 3. Direction: "Waypoint" — a topographic wayfinding instrument

**Thesis.** A cairn is a stack of stones that marks a route across terrain. A version
control system marks points in history and lets you navigate between them. The whole
interface is built as a field instrument for reading terrain: a survey sheet, not a
dark IDE and not a GitHub reskin. Commits are waypoints, branches are trail routes, the
DAG is a route map over contour lines, and the metadata reads like the marginalia on a
topographic quad sheet.

**Why not the obvious answer.** The default a tool like this pulls toward is a near-black
background with one bright accent, monospace everywhere, and hairline rules. That is a
Linear/Vercel pastiche and it is subject-blind. This direction instead takes its palette
from map legends, its structural motifs from contour lines and wayfinding signage, and
its type from signage lineage. The one real aesthetic risk is the light survey-paper base
for a code tool; it is justified because field guides and maps are paper, and it is what
makes the artifact memorable instead of familiar. A "night navigation" dark mode is
provided for code-heavy reading.

---

## 4. Non-negotiable constraints

- **Keep the plumbing.** No endpoint, route, loader, or data-shape changes. If a screen
  needs data the API does not expose, note it and stop; do not add backend code.
- **Distinct skin, conventional grammar.** The identity is visual. Interaction stays
  legible: a diff reads like a diff, the tree navigates like a tree, the PR flow is
  standard. Do not reinvent the UX of core dev surfaces.
- **Quality floor, unannounced.** WCAG AA contrast (verify `--route` on both `--paper`
  and `--surface`), a visible focus ring on every interactive element, full keyboard
  navigation, `prefers-reduced-motion` honored, responsive down to tablet without
  breakage.
- **Preserve virtualization** on `CodeViewer` and `DiffView`. The redesign must not
  reintroduce full-list rendering.
- **Fonts** self-hosted or via Google Fonts with `display: swap` and no layout shift.
- **Spend boldness once.** The route-map commit graph is the memorable element.
  Everything around it stays quiet and disciplined.

---

## 5. Design tokens

Implement as CSS custom properties. Two themes: light "survey sheet" (default) and
"night navigation". Honor `prefers-color-scheme`, with a manual toggle that overrides.

### Color — light (default)

```css
:root {
  --paper:          #ECEEE8;  /* survey-sheet base (cool paper, not cream) */
  --surface:        #F5F6F1;  /* raised panels, cards */
  --surface-sunken: #E3E6DD;  /* wells, code gutters */
  --ink:            #1B2019;  /* primary text, linework */
  --ink-2:          #4A5247;  /* secondary text */
  --ink-muted:      #7C8378;  /* tertiary, timestamps */
  --hairline:       #D6CFC0;  /* warm 1px structural borders */
  --contour:        #C9A46B;  /* ochre topographic linework / dividers */

  --route:          #B5297E;  /* primary wayfinding accent (trail route) */
  --route-ink:      #8E1F62;  /* magenta as small text on light */
  --route-tint:     rgba(181,41,126,0.10);
  --on-route:       #FBF7F2;  /* text on a --route fill */

  --water:          #2E6E8E;  /* links, info */
  --veg:            #3E7D4F;  /* success, diff add */
  --survey-red:     #C0392B;  /* danger, diff remove */
  --caution:        #C98A1E;  /* warning, merge conflict */

  --diff-add-bg:    rgba(62,125,79,0.12);
  --diff-add-ink:   #2C5E39;
  --diff-del-bg:    rgba(192,57,43,0.12);
  --diff-del-ink:   #8F2A20;
  --conflict-bg:    rgba(201,138,30,0.14);

  --focus:          var(--route);
  --shadow-1:       0 1px 2px rgba(27,32,25,0.06); /* overlays/menus only */
}
```

### Color — night navigation

```css
[data-theme="night"] {
  --paper:          #15170F;
  --surface:        #1D1F16;
  --surface-sunken: #101208;
  --ink:            #E8EAE0;
  --ink-2:          #AEB3A4;
  --ink-muted:      #767C6C;
  --hairline:       #2E3126;
  --contour:        #6E5E3C;

  --route:          #E85AA6;
  --route-ink:      #F19FCB;
  --route-tint:     rgba(232,90,166,0.14);
  --on-route:       #15170F;

  --water:          #5AA6C4;
  --veg:            #6BBE7E;
  --survey-red:     #E06A5C;
  --caution:        #E0AE4A;

  --diff-add-bg:    rgba(107,190,126,0.14);
  --diff-add-ink:   #9BD8A8;
  --diff-del-bg:    rgba(224,106,92,0.14);
  --diff-del-ink:   #F0A79C;
  --conflict-bg:    rgba(224,174,74,0.16);
}
```

Note: night mode keeps the full map-legend palette (route + water + veg), not a single
accent. That is deliberate; a single-accent dark theme is the cliché this avoids.

### Type

Signage lineage, chosen for the wayfinding subject rather than reached for by default.
Overpass descends from highway signage; Overpass Mono is its matched data face.

```css
:root {
  --font-display: "Overpass", ui-sans-serif, system-ui, sans-serif; /* 700, hero */
  --font-ui:      "Overpass", ui-sans-serif, system-ui, sans-serif; /* 400/500/600 */
  --font-mono:    "Overpass Mono", ui-monospace, "SF Mono", monospace;
}
```

Scale (dev-tool dense; base is 14px):

| token | size | use |
|---|---|---|
| `--text-xs` | 0.75rem | badges, gutters, marginalia |
| `--text-sm` | 0.8125rem | secondary UI, metadata |
| `--text-base` | 0.875rem | body UI default |
| `--text-md` | 1rem | emphasized body, subheads |
| `--text-lg` | 1.25rem | section titles |
| `--text-xl` | 1.75rem | page titles |
| `--display` | 2.75rem | hero (repo name, auth), weight 700, letter-spacing -0.02em |

**Mono signature rule.** Every structural datum renders in `--font-mono`: commit hashes,
ref and branch names, file paths, timestamps, byte sizes, and line/column numbers. This
is the single most consistent identity cue. Prose (READMEs, comments, issue bodies,
descriptions) stays in `--font-ui`.

### Space, radius, borders, elevation, motion

```css
:root {
  --sp-1:4px; --sp-2:8px; --sp-3:12px; --sp-4:16px;
  --sp-5:24px; --sp-6:32px; --sp-8:48px; --sp-10:64px;

  --r-sm:3px; --r:5px; --r-lg:8px;      /* precise, instrument-like; no pills */

  --dur:140ms; --ease:cubic-bezier(0.2,0,0,1);
}
```

- Borders are the primary structural device: 1px `--hairline`, or `--contour` where a
  divider should read as topographic. Define panels with borders + surface steps, not
  shadow. Reserve `--shadow-1` for floating overlays only.
- Radius is small and consistent. The only full-round element is an avatar (with a
  hairline ring).
- Motion is functional and short. One orchestrated moment only (see signature). Under
  `prefers-reduced-motion`, disable the route-trace and all non-essential transitions.

---

## 6. Signature element: the route-map commit graph

The DAG is Cairn's proof of engine. Render it as a trail route on a topographic map. This
is the hero on the repo home and the centerpiece of a dedicated history view.

- **Branch lines** are trail routes. The default/active branch is a solid `--route`
  stroke; other branches are thinner `--ink-muted`.
- **Commit nodes** are small cairn glyphs (a 3-stone stack). `HEAD`/tip is a filled
  summit marker in `--route` with a subtle ring.
- **Merge points** are trail junctions (converging routes into a small diamond).
- **Lane layout** must be topologically honest. Consume the commit-graph data the API
  already returns (generation numbers / parent links); do not invent an ordering. If the
  API exposes lane or generation info, use it; if it only returns parents, derive lanes
  minimally in the client from data already fetched. Do not add a backend endpoint.
- **Contour backing** (optional): very faint contour linework behind the graph at ~0.06
  opacity. Never behind code text.
- **Load motion:** the active route traces in over ~600ms, like a route being drawn on a
  map. Reduced-motion: it appears instantly.

```
ROUTE
  ◆ HEAD   a3f9c1  fix: race in Hikari pool init
  │
  ●        7b21e4  feat: REBASE merge strategy
  ●╮       9c02aa  merge #1  review composer
  │●       4d88f0  add trigram index
  ●╯       1af330  labels, milestones, assignees
```

Supporting motifs (structure encodes content, per the "structure is information" rule):

- **Breadcrumb as a route:** `acme ▸ demo ▸ main ▸ src/store` in mono, waypoint
  separators. Not decorative; it is the path.
- **Cairn wordmark/mark:** a stacked-stones glyph for the logo, favicon, commit nodes,
  and empty-state motif.
- **Dividers as contour rules** where a section break benefits from reading topographic
  (`--contour`), plain `--hairline` elsewhere.
- Do **not** add generic `01 / 02 / 03` numbering anywhere except the commit timeline,
  which is a real sequence.

---

## 7. App shell and wayfinding

```
┌───────────────────────────────────────────────────────────────┐
│ ▲ cairn   [ search repos, code, refs…        ]         ◉ acme  │  top bar
├───────────────────────────────────────────────────────────────┤
│ acme ▸ demo             main ▾   https://…/demo.git ⧉  [Clone] │  repo header (mono)
│ Code   Issues 3   Pull requests 1   Search   Settings          │  signage tabs
├─────────────────────────────────┬─────────────────────────────┤
│ ROUTE  (commit-graph teaser)    │  About                      │
│  ◆ HEAD  fix: race in pool      │  A demo repo.               │
│  ●  feat: rebase strategy       │  ──── contour rule ────     │
│  ●╮ merge #1                    │  Languages · Java 71% · TS  │
│  │● add trigram index           │  42 commits · 3 branches    │
│  ●╯                             │  Clone   https://… ⧉        │
├─────────────────────────────────┴─────────────────────────────┤
│ Files   src/ · docs/ · README.md · build.gradle …             │
│ README.md rendered …                                          │
└───────────────────────────────────────────────────────────────┘
```

- **Top bar:** cairn mark + wordmark left, global search (mono input) center, account
  right. `--route` is reserved for active/primary states only.
- **Repo header:** mono breadcrumb route, ref selector, the clone URL shown in mono with
  a copy control, a primary action.
- **Section nav:** signage tabs, mono labels, `--route` underline when active.
- **Content layouts:** browse = tree + content; PR/issue = main + meta-sidebar.

---

## 8. Component direction

- **Buttons:** rectangular, `--r`. Primary = `--route` fill with `--on-route` text;
  secondary = hairline ghost; destructive = `--survey-red`. Confident, matte, not glossy.
- **Inputs / search:** mono text, hairline border, `--route` focus ring. The global
  search reads like a map coordinate/search field.
- **Tabs:** signage style, mono labels, `--route` active underline.
- **Badges / labels:** `--r-sm`, mono, small. Issue labels keep author colors but
  constrained to a tasteful set; system badges use `--water` / `--veg` / `--caution`.
  State chips (open / merged / closed) map to `--veg` / `--route` / `--ink-muted`.
- **CodeViewer:** mono, line-height 1.6, muted mono line numbers, a light map-legend
  syntax theme (keyword `--route-ink`, string `--veg`, number `--water`, comment
  `--ink-muted` italic, function `#8A5A2B`). Blame gutter: compact mono, age-tinted
  (older = fainter). Keep virtualization.
- **DiffView:** unified and split. Add = `--diff-add`, remove = `--diff-del`, hunk headers
  in mono over a `--contour` rule. Conflict regions in `--conflict-bg` with an explicit
  labeled marker. Keep virtualization.
- **Files-changed:** a legend of touched files with +/- counts in mono.
- **PR conversation (`MergeBox`, `ReviewComposer`):** a vertical route/timeline; events
  (commits, reviews, comments, merge) as waypoints with cairn/summit markers. `MergeBox`
  is the trail-junction decision panel: state, checks, the merge action. `ReviewComposer`
  stays clean and standard.
- **Avatars:** the one full-round element, hairline ring.

---

## 9. Screens and states

**Priority (recruiter-path, take to flagship quality):**

1. **Repo home** — the flagship, and the most-polished screen. Route commit-graph teaser,
   file tree + rendered README, ref selector, clone URL (mono, copyable), repo meta
   (about, a small languages legend, counts in mono).
2. **Tree / file browse** — tree nav, file view with syntax + blame toggle, path
   breadcrumb route, raw/history actions.
3. **Diff / commit view** — commit metadata (author, hash in mono, message), the styled
   diff, files-changed legend.
4. **Pull request** — state chip, conversation timeline, Files-changed and Commits tabs,
   `MergeBox`, `ReviewComposer`.

**Secondary (clean quality):**

5. **Code search** — prominent mono search; results with trigram matches highlighted in
   `--route-tint`.
6. **Issues** — list + detail with labels, milestones, assignees (already built).
7. **Org / team / grant access** — instrument-panel forms; quiet and legible.
8. **Auth (signup / login)** — a survey-sheet page with faint contour texture and the
   cairn mark; a deliberate branded moment.
9. **`/{owner}` profile (NEW)** — fixes the header 404. Minimal: mark/avatar, name, and a
   list of that owner's repos. In scope because the recruiter clicks it.

**Every screen ships four states** — empty (invites action, contour + cairn motif),
loading (skeletons in the shell style), error (direct, non-apologetic, tells the user what
happened and how to fix it) — plus **conflict** for diff and merge surfaces.

---

## 10. Voice

Copy is design material. End-user language, not system internals. Active voice; an action
keeps its name through the flow ("Merge" produces "Merged"). Errors state what went wrong
and the next step, and do not apologize. Empty screens invite the next action. Sentence
case throughout.

---

## 11. Definition of done

- `web/` builds and typechecks clean.
- Token system implemented as CSS custom properties, both themes, toggle honoring
  `prefers-color-scheme`.
- The four priority screens fully realized in-direction, all states including conflict.
- Route-map commit graph implemented as specified, driven by existing API data.
- `/{owner}` profile page exists and the header link resolves.
- Quality floor met: AA contrast (route verified on paper and surface), visible focus,
  keyboard nav, reduced-motion, responsive to tablet.
- Virtualization preserved on `CodeViewer` and `DiffView`.
- Fonts loaded with `display: swap`, no layout shift.
- No change to routing, data-fetching, API calls, or any backend/test file.
- Screenshots of the four priority screens (light and night) for review, if the build
  environment can produce them.
