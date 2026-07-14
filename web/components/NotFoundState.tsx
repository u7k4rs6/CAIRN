import { CairnMark } from "@/components/CairnMark";

/**
 * Frontend spec, section 8: private and nonexistent resources render identically as
 * "not found," so a denied request never confirms a resource's existence. Voice
 * (redesign spec, section 10): direct, tells the reader what happened and what to
 * try next, no apology.
 */
export function NotFoundState({ label = "repository" }: { label?: string }) {
  return (
    <div className="contour-bg max-w-lg mx-auto px-4 py-24 text-center flex flex-col items-center gap-3">
      <CairnMark size={36} className="text-ink-muted" />
      <h1 className="text-xl font-display font-bold text-ink">Off the map</h1>
      <p className="text-ink-2 text-sm">
        This {label} isn&rsquo;t here, or you don&rsquo;t have access to it. Check the path, or sign in if you expect to see it.
      </p>
    </div>
  );
}
