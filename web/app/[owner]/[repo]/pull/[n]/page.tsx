import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { StateBadge } from "@/components/StateBadge";
import { NotFoundState } from "@/components/NotFoundState";
import { AuthBar } from "@/components/AuthBar";
import { ReviewComposer } from "@/components/ReviewComposer";
import { MergeBox } from "@/components/MergeBox";
import { DiffView } from "@/components/DiffView";
import { CommitRow } from "@/components/CommitRow";

function stripRefPrefix(ref: string): string {
  return ref.replace(/^refs\/heads\//, "");
}

/** Frontend spec, section 5.6: three tabs (Conversation, Files changed, Commits). */
export default async function PullRequestDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ owner: string; repo: string; n: string }>;
  searchParams: Promise<{ tab?: string }>;
}) {
  const { owner, repo, n } = await params;
  const { tab } = await searchParams;
  const activeTab = tab === "files" || tab === "commits" ? tab : "conversation";
  const pr = await api.pull(owner, repo, n);

  if (!pr) {
    return <NotFoundState label="pull request" />;
  }

  // A merged or closed PR's source branch may no longer exist; the Conversation
  // tab must still render, so a missing ref degrades to empty tabs rather than a
  // crash (frontend spec, section 8: an error state, not a raw failure).
  const compare = await api
    .compare(owner, repo, stripRefPrefix(pr.targetRef), stripRefPrefix(pr.sourceRef))
    .catch((e) => {
      if (e instanceof ApiError) {
        return { commits: [], diffs: [] };
      }
      throw e;
    });

  const tabs = [
    { key: "conversation", label: "Conversation" },
    { key: "files", label: `Files changed (${compare.diffs.length})` },
    { key: "commits", label: `Commits (${compare.commits.length})` },
  ];

  return (
    <div>
      <RepoHeader owner={owner} repo={repo} active="pulls" />
      <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-4">
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

        <nav className="flex gap-4 text-sm border-b border-border">
          {tabs.map((t) => (
            <Link
              key={t.key}
              href={`/${owner}/${repo}/pull/${n}${t.key === "conversation" ? "" : `?tab=${t.key}`}`}
              className={`pb-2 border-b-2 ${
                activeTab === t.key ? "border-accent font-medium" : "border-transparent text-fg-muted"
              }`}
            >
              {t.label}
            </Link>
          ))}
        </nav>

        {activeTab === "conversation" && (
          <>
            <AuthBar />
            <MergeBox owner={owner} repo={repo} number={pr.id} state={pr.state} />
            <ReviewComposer owner={owner} repo={repo} number={pr.id} />
          </>
        )}

        {activeTab === "files" && <DiffView diffs={compare.diffs} />}

        {activeTab === "commits" && (
          <div className="border border-border rounded overflow-hidden">
            {compare.commits.length === 0 ? (
              <p className="text-fg-muted text-sm px-4 py-8">No commits.</p>
            ) : (
              compare.commits.map((commit) => <CommitRow key={commit.id} owner={owner} repo={repo} commit={commit} />)
            )}
          </div>
        )}
      </div>
    </div>
  );
}
