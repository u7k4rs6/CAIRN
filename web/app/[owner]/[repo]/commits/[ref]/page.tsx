import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";
import { RouteGraph } from "@/components/RouteGraph";
import { CairnMark } from "@/components/CairnMark";

/** The dedicated history view (redesign spec, section 6): the full route graph, not a teaser. */
export default async function CommitsPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; ref: string }>;
}) {
  const { owner, repo, ref } = await params;

  try {
    const commits = await api.commits(owner, repo, ref);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" gitRef={ref} />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <h1 className="font-mono text-xs uppercase tracking-wide text-ink-muted mb-2">
            Route &middot; {commits.length} waypoint{commits.length === 1 ? "" : "s"} on {ref}
          </h1>
          {commits.length === 0 ? (
            <div className="contour-bg border border-hairline rounded p-8 text-center flex flex-col items-center gap-3">
              <CairnMark size={28} className="text-ink-muted" />
              <p className="text-ink-2 text-sm">No waypoints yet on this branch.</p>
            </div>
          ) : (
            <div className="border border-hairline rounded p-3 bg-surface">
              <RouteGraph owner={owner} repo={repo} commits={commits} />
            </div>
          )}
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="repository" />;
    }
    throw e;
  }
}
