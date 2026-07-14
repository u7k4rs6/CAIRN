/**
 * Where this app reaches the Cairn API from - the one place that decides it, so
 * every fetch (Server Component or client island) asks the same question instead
 * of each carrying its own copy of the logic.
 *
 * Server Components, route handlers, and SSR run inside Railway's own network,
 * where the API's public URL hairpins (leaves the network, round-trips back in)
 * and times out; only the private `*.railway.internal` address is reliably
 * reachable there, so the server branch is always absolute and always private.
 *
 * The browser is the opposite case: it must never see or call the API's public
 * URL directly, because {@code cairn_session} is only delivered reliably as a
 * first-party, same-origin cookie. Client fetches resolve to "" (relative), so a
 * path like {@code `${apiBase()}/api/...`} becomes same-origin {@code /api/...},
 * which next.config.ts's rewrite proxies server-side to the private API.
 */
export function apiBase(): string {
  if (typeof window !== "undefined") {
    return "";
  }
  return process.env.INTERNAL_API_URL || "http://localhost:8080";
}

/**
 * The API's public address - the one legitimate reason to expose it is a
 * `git clone` URL shown to the user, since the git client running it is on the
 * visitor's own machine, outside Railway's network entirely. Never use this for
 * a fetch; see {@link apiBase} for that.
 */
export function publicGitBase(): string {
  return process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";
}
