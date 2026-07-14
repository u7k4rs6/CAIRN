/** Redesign spec, section 8: state chips map to --veg (open/approved), --route (merged), --ink-muted (closed/draft), --caution otherwise. */
const COLORS: Record<string, string> = {
  OPEN: "bg-veg/15 text-veg",
  DRAFT: "bg-ink-muted/15 text-ink-2",
  REVIEW_REQUESTED: "bg-caution/15 text-caution",
  APPROVED: "bg-veg/15 text-veg",
  CHANGES_REQUESTED: "bg-survey-red/15 text-survey-red",
  MERGED: "bg-route/15 text-route-ink",
  CLOSED: "bg-ink-muted/15 text-ink-2",
  // Repo visibility (the /{owner} repo list): public is the "open" case, private
  // the one worth calling out, internal a middle ground.
  PUBLIC: "bg-veg/15 text-veg",
  INTERNAL: "bg-water/15 text-water",
  PRIVATE: "bg-caution/15 text-caution",
};

export function StateBadge({ state }: { state: string }) {
  const classes = COLORS[state] ?? "bg-ink-muted/15 text-ink-2";
  return <span className={`state-chip ${classes}`}>{state.replace(/_/g, " ").toLowerCase()}</span>;
}
