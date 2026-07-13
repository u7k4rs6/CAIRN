# Cairn

Cairn is a self-hosted Git hosting and collaboration platform, built as a portfolio
and interview artifact. The point of the project isn't the CRUD screens around
issues and pull requests — plenty of software does that. The point is underneath
them: a real content-addressable version-control engine written from scratch in
Java, not a database with Git-shaped column names. Objects are hashed and stored
exactly the way Git hashes and stores them, packfiles use real delta encoding, merges
are computed with an actual three-way merge over a real diff algorithm, and every one
of those operations is cross-checked against a real `git` binary in the test suite,
not just against its own code.

## 60-second architecture summary

Four Gradle modules, dependencies pointing inward only:

```
cairn-vcs  <---  cairn-transfer  <---  cairn-api  <---  web
(engine)         (smart-HTTP           (auth, perms,      (Next.js)
                  transport)            collaboration,
                                        REST)
```

- **`cairn-vcs`** is a standalone library with zero dependencies on a database or a
  web framework. It compiles and tests on its own: object store, commit DAG,
  generation numbers, Myers diff, three-way merge, packfiles with delta encoding, a
  trigram search index, and blame. This is deliberate — the engine should be legible
  and testable as a thing in itself, the same way you'd judge a real systems library.
- **`cairn-transfer`** implements Git's smart-HTTP negotiation (`want`/`have`,
  `missing = reachable(wants) \ reachable(haves)`) on top of the engine.
- **`cairn-api`** is the platform: permissions, teams and orgs, issues, pull
  requests, reviews, code search, all as a Spring Boot REST API sitting on top of
  the engine — the engine itself knows nothing about any of this.
- **`web`** is the Next.js frontend: Server Components for read paths (so a signed-in
  user's session is honored on first render, not just via client-side fetches), a
  handful of client islands for the interactive write paths (login, PR merge,
  reviews, comments).

Where a data structure or algorithm was chosen over an available alternative, the
code says why inline (Javadoc on the class), and:

- **[`docs/COMPLEXITY.md`](docs/COMPLEXITY.md)** is the per-operation complexity and
  tradeoff reference: object read/write, ref resolution, DAG walk, merge-base, diff,
  three-way merge, packfile encode/decode, transfer negotiation, permission
  resolution, trigram index build/query, blame — each cited to the exact file and
  method that implements it, with every bound checked against what the code actually
  does rather than what a textbook says it should do (in a couple of places, reading
  the code turned up a tighter or more honest bound than the original design doc
  assumed, and the doc says so explicitly rather than quietly keeping the old
  number).
- **[`docs/HLD.md`](docs/HLD.md)** is the scale narrative: what actually bottlenecks
  in this codebase today (one filesystem for the whole object store, one in-memory
  search index per process, one relational schema), a sharding strategy grounded in
  a key the code already uses (`RepositoryRegistry`'s `{owner}/{repo}`), what breaks
  at that shard boundary, and a clearly-marked "designed, not built" section for
  partial clone and reachability bitmaps.

These two docs are the reason this project exists as an interview artifact, more
than any single screen in the UI: they're where you can check whether the reasoning
behind the systems work holds up, not just whether the tests pass.

## Quickstart

Requires Docker and Docker Compose.

```bash
docker compose up -d
```

This builds and starts three containers: `db` (Postgres, for real persistence across
restarts — the app's own default is an in-memory H2 database that doesn't survive a
JVM restart, so compose overrides it to Postgres, which is already on the classpath
for exactly this purpose), `api` (the Spring Boot backend on `:8080`, seeded on first
boot with a demo repo, an issue, and a pull request), and `web` (the Next.js frontend
on `:3000`).

Once it's up:

- **Browse the seeded repo anonymously** (it's public): [http://localhost:3000/acme/demo](http://localhost:3000/acme/demo)
- **Find the seeded personal access token** in the API's logs:
  ```bash
  docker compose logs api | grep -A3 "Cairn dev data seeded"
  ```
  Use it as a Basic Auth credential (`acme` / the printed token) against the API,
  e.g.:
  ```bash
  curl -u acme:<token> http://localhost:8080/api/repos/acme/demo/commits/main
  ```
- The web login form (`/login`) authenticates a real username/password account
  created via `/signup` and a server-side session cookie — it's a separate flow from
  the seeded PAT above, which is meant for API/`git`-client-style access, not the
  browser session.

State (the Postgres database and the Git object store) is kept in named Docker
volumes, so it survives `docker compose down` and `docker compose up` again. Use
`docker compose down -v` to reset to a clean seeded state.

## What's not built

Named plainly rather than left implied, in order of what would matter most to a real
user:

- **Line-anchored review comments have no UI** (FR-COLLAB-3). The API and domain
  fully support a review's `path`/`line`; the frontend's `ReviewComposer` never
  offers a way to set them, so every review is body-level only even though the
  "Files changed" diff view is right there.
- **Pull requests have no labels, milestones, or assignees** — issues have all
  three. A deliberate scope cut, made to avoid repeating the exact "API with no UI
  behind it" gap this project's own gap-closure round exists to close for issues.
- **No `/{owner}` user/org profile page.** Named in the frontend spec's own route
  table, never built; the repo header's owner breadcrumb link 404s.
- **No SSH transport.** Only Git-over-HTTP exists — no SSH server, no public-key
  registration, no `settings/keys` UI. The PRD doesn't mark this paper-only, so it's
  named here as a real gap, not an implied one.

See [`SUMMARY.md`](SUMMARY.md) for the full FR-by-FR completion audit this list is
drawn from, and [`docs/HLD.md`](docs/HLD.md)'s "Designed, not built" section for
partial clone/sparse checkout and reachability bitmaps specifically — those are
paper-only by the PRD's own design, not gaps.
