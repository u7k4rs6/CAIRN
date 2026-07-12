const COLORS: Record<string, string> = {
  OPEN: "bg-success/15 text-success",
  DRAFT: "bg-fg-muted/15 text-fg-muted",
  REVIEW_REQUESTED: "bg-warning/15 text-warning",
  APPROVED: "bg-success/15 text-success",
  CHANGES_REQUESTED: "bg-danger/15 text-danger",
  MERGED: "bg-accent/15 text-accent",
  CLOSED: "bg-danger/15 text-danger",
};

export function StateBadge({ state }: { state: string }) {
  const classes = COLORS[state] ?? "bg-fg-muted/15 text-fg-muted";
  return <span className={`state-badge ${classes}`}>{state.replace(/_/g, " ")}</span>;
}
