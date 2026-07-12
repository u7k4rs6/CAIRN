/**
 * Frontend spec, section 8: private and nonexistent resources render identically as
 * "not found," so a denied request never confirms a resource's existence.
 */
export function NotFoundState({ label = "repository" }: { label?: string }) {
  return (
    <div className="max-w-lg mx-auto px-4 py-24 text-center">
      <h1 className="text-xl font-semibold mb-2">404</h1>
      <p className="text-fg-muted">This {label} does not exist, or you do not have access to it.</p>
    </div>
  );
}
