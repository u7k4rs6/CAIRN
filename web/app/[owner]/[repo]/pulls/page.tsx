import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { StateBadge } from "@/components/StateBadge";
import { NotFoundState } from "@/components/NotFoundState";

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
            <p className="text-fg-muted text-sm py-8">No pull requests yet.</p>
          ) : (
            <div className="border border-border rounded overflow-hidden">
              {pulls.map((pr) => (
                <div key={pr.id} className="flex items-center justify-between px-3 py-2 border-b border-border last:border-0 text-sm">
                  <div>
                    <Link href={`/${owner}/${repo}/pull/${pr.id}`} className="font-medium hover:underline">
                      {pr.title}
                    </Link>
                    <div className="text-fg-muted text-xs font-mono-data">
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
