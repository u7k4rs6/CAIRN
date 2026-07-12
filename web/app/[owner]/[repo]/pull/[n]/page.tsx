import { api } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { StateBadge } from "@/components/StateBadge";
import { NotFoundState } from "@/components/NotFoundState";
import { AuthBar } from "@/components/AuthBar";
import { ReviewComposer } from "@/components/ReviewComposer";
import { MergeBox } from "@/components/MergeBox";

export default async function PullRequestDetailPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; n: string }>;
}) {
  const { owner, repo, n } = await params;
  const pr = await api.pull(owner, repo, n);

  if (!pr) {
    return <NotFoundState label="pull request" />;
  }

  return (
    <div>
      <RepoHeader owner={owner} repo={repo} active="pulls" />
      <div className="px-4 py-4 max-w-3xl mx-auto flex flex-col gap-4">
        <div>
          <h1 className="text-lg font-semibold">
            {pr.title} <span className="text-fg-muted font-normal">#{pr.id}</span>
          </h1>
          <div className="flex items-center gap-2 mt-1">
            <StateBadge state={pr.state} />
            <span className="text-fg-muted text-sm font-mono-data">
              {pr.author?.username} wants to merge {pr.sourceRef} into {pr.targetRef}
            </span>
          </div>
        </div>

        <AuthBar />
        <MergeBox owner={owner} repo={repo} number={pr.id} state={pr.state} />
        <ReviewComposer owner={owner} repo={repo} number={pr.id} />
      </div>
    </div>
  );
}
