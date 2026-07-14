/**
 * Accepts "owner/repo", a leading-slash path, or a full URL (this site's own or
 * a pasted GitHub one) and resolves it to "owner/repo" - the last two path
 * segments once any scheme/host is stripped. Returns null for anything that
 * doesn't resolve to at least two segments, so callers can silently ignore it
 * rather than navigate somewhere broken.
 */
export function parseOwnerRepoPath(raw: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }

  let path = trimmed;
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed)) {
    try {
      path = new URL(trimmed).pathname;
    } catch {
      return null;
    }
  }

  const segments = path.split("/").filter(Boolean);
  if (segments.length < 2) {
    return null;
  }
  const [owner, repo] = segments.slice(-2);
  return `${owner}/${repo}`;
}
