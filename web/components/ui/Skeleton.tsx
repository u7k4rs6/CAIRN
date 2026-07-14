/** Shell-style loading skeleton (redesign spec, section 9): a plain muted block, not a shimmering placeholder for content that hasn't been designed yet. */
export function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`bg-surface-sunken rounded animate-pulse ${className}`} />;
}
