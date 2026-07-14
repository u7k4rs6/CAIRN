import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { StateBadge } from "@/components/StateBadge";
import { NotFoundState } from "@/components/NotFoundState";
import { CairnMark } from "@/components/CairnMark";

export default async function PullsPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string }>;
}) {
  const { owner, repo } = await params;

  try {
    const pulls = await api.pulls(owner, repo);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="pulls" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          {pulls.length === 0 ? (
            <div className="contour-bg border border-hairline rounded p-8 text-center flex flex-col items-center gap-3">
              <CairnMark size={28} className="text-ink-muted" />
              <p className="text-ink-2 text-sm">No pull requests yet. Open one to propose a route into this branch.</p>
            </div>
          ) : (
            <div className="border border-hairline rounded overflow-hidden">
              {pulls.map((pr) => (
                <div key={pr.id} className="flex items-center justify-between px-3 py-2 border-b border-hairline last:border-0 text-sm">
                  <div className="min-w-0">
                    <Link href={`/${owner}/${repo}/pull/${pr.id}`} className="font-medium text-ink hover:text-route hover:underline">
                      {pr.title}
                    </Link>
                    <div className="text-ink-muted text-xs font-mono">
                      {pr.sourceRef} &rarr; {pr.targetRef}
                    </div>
                  </div>
                  <StateBadge state={pr.state} />
                </div>
              ))}
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
