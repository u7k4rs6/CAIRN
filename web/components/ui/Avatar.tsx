const PALETTE = ["var(--route)", "var(--water)", "var(--veg)", "var(--caution)", "var(--route-ink)"];

function colorFor(username: string): string {
  let hash = 0;
  for (let i = 0; i < username.length; i++) {
    hash = (hash * 31 + username.charCodeAt(i)) | 0;
  }
  return PALETTE[Math.abs(hash) % PALETTE.length];
}

/** The one full-round element in the system, with a hairline ring (redesign spec, section 8). No avatar images exist in this API, so this is a lettered mark, not a photo stand-in. */
export function Avatar({ username, size = 20 }: { username: string; size?: number }) {
  return (
    <span
      role="img"
      aria-label={username}
      title={username}
      style={{ width: size, height: size, backgroundColor: colorFor(username), fontSize: size * 0.5 }}
      className="inline-flex items-center justify-center rounded-full text-on-route font-display font-bold ring-1 ring-hairline shrink-0 select-none"
    >
      {username.charAt(0).toUpperCase()}
    </span>
  );
}
