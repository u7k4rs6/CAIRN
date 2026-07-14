/**
 * The wordmark glyph (redesign spec, section 6): a stacked-stones cairn, three
 * irregular rounded forms narrowing upward. Used in the top bar, favicon, commit
 * nodes (as the base for the route-graph waypoint), and empty-state motifs.
 * `currentColor` throughout so it inherits ink/route/muted from its context.
 */
export function CairnMark({ size = 20, className }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <ellipse cx="12" cy="19.5" rx="9" ry="2.6" fill="currentColor" opacity="0.9" />
      <ellipse cx="12.5" cy="13.5" rx="6.4" ry="2.3" fill="currentColor" opacity="0.95" />
      <ellipse cx="11.5" cy="7.8" rx="4" ry="1.8" fill="currentColor" />
      <ellipse cx="12" cy="3.4" rx="2" ry="1.3" fill="currentColor" />
    </svg>
  );
}
