import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { StateBadge } from "@/components/StateBadge";
import { NotFoundState } from "@/components/NotFoundState";
import { AuthBar } from "@/components/AuthBar";
import { ReviewComposer } from "@/components/ReviewComposer";
import { MergeBox } from "@/components/MergeBox";
import { DiffView } from "@/components/DiffView";
import { CommitRow } from "@/components/CommitRow";
import { SignageTabs } from "@/components/ui/SignageTabs";
import { CairnMark } from "@/components/CairnMark";
import { Avatar } from "@/components/ui/Avatar";

function stripRefPrefix(ref: string): string {
  return ref.replace(/^refs\/heads\//, "");
}

const VERDICT_LABEL: Record<string, string> = {
  APPROVE: "approved",
  REQUEST_CHANGES: "requested changes",
  COMMENT: "commented",
};

/**
 * Frontend spec, section 5.6: three tabs (Conversation, Files changed, Commits),
 * the conversation rendered as a vertical route/timeline of waypoints. Now backed
 * by `GET .../pulls/{n}/conversation` (this session's endpoint): the PR's real
 * reviews and comments render as waypoints, not just the opening metadata.
 */
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
  const [compare, conversation] = await Promise.all([
    api.compare(owner, repo, stripRefPrefix(pr.targetRef), stripRefPrefix(pr.sourceRef)).catch((e) => {
      if (e instanceof ApiError) {
        return { commits: [], diffs: [] };
      }
      throw e;
    }),
    api.conversation(owner, repo, pr.id).catch(() => []),
  ]);
  const lineComments = conversation.filter((entry) => entry.path !== null && entry.line !== null);

  const tabs = [
    { key: "conversation", label: "Conversation", href: `/${owner}/${repo}/pull/${n}` },
    { key: "files", label: `Files changed (${compare.diffs.length})`, href: `/${owner}/${repo}/pull/${n}?tab=files` },
    { key: "commits", label: `Commits (${compare.commits.length})`, href: `/${owner}/${repo}/pull/${n}?tab=commits` },
  ];

  return (
    <div>
      <RepoHeader owner={owner} repo={repo} active="pulls" />
      <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-4">
        <div>
          <h1 className="text-lg font-display font-bold text-ink">
            {pr.title} <span className="text-ink-muted font-normal">#{pr.id}</span>
          </h1>
          <div className="flex items-center gap-2 mt-1">
            <StateBadge state={pr.state} />
            <span className="text-ink-2 text-sm">
              {pr.author?.username} wants to merge <span className="font-mono">{pr.sourceRef}</span> into{" "}
              <span className="font-mono">{pr.targetRef}</span>
            </span>
          </div>
        </div>

        <SignageTabs tabs={tabs} active={activeTab} />

        {activeTab === "conversation" && (
          <div className="relative pl-7 flex flex-col gap-5">
            <div aria-hidden="true" className="absolute left-[9px] top-2 bottom-2 w-px bg-hairline" />

            <div className="relative">
              <CairnMark size={16} className="absolute -left-7 top-0.5 text-route" />
              <p className="text-sm text-ink-2">
                <span className="font-medium text-ink">{pr.author?.username}</span> opened this route from{" "}
                <span className="font-mono">{pr.sourceRef}</span> into <span className="font-mono">{pr.targetRef}</span>
              </p>
            </div>

            {conversation.map((entry) => (
              <div key={`${entry.kind}-${entry.id}`} className="relative">
                <CairnMark
                  size={16}
                  className={`absolute -left-7 top-0.5 ${entry.verdict === "APPROVE" ? "text-veg" : entry.verdict === "REQUEST_CHANGES" ? "text-survey-red" : "text-ink-muted"}`}
                />
                <div className="flex items-start gap-2">
                  <Avatar username={entry.author} size={18} />
                  <div className="min-w-0">
                    <p className="text-sm">
                      <span className="font-medium text-ink">{entry.author}</span>{" "}
                      <span className="text-ink-muted">
                        {entry.kind === "REVIEW" ? VERDICT_LABEL[entry.verdict ?? "COMMENT"] : "commented"}
                        {entry.path && (
                          <>
                            {" "}
                            on <span className="font-mono text-ink-2">{entry.path}:{entry.line}</span>
                          </>
                        )}
                      </span>
                    </p>
                    {entry.body && <p className="text-sm text-ink-2 whitespace-pre-wrap mt-0.5">{entry.body}</p>}
                  </div>
                </div>
              </div>
            ))}

            <AuthBar />

            <div className="relative">
              <CairnMark size={16} className="absolute -left-7 top-0.5 text-ink-muted" />
              <ReviewComposer owner={owner} repo={repo} number={pr.id} />
            </div>

            <div className="relative">
              <CairnMark size={16} className="absolute -left-7 top-0.5 text-ink-muted" />
              <MergeBox owner={owner} repo={repo} number={pr.id} state={pr.state} />
            </div>
          </div>
        )}

        {activeTab === "files" && (
          <DiffView diffs={compare.diffs} owner={owner} repo={repo} prNumber={pr.id} lineComments={lineComments} />
        )}

        {activeTab === "commits" && (
          <div className="border border-hairline rounded overflow-hidden">
            {compare.commits.length === 0 ? (
              <p className="text-ink-muted text-sm px-4 py-8">No commits on this route.</p>
            ) : (
              compare.commits.map((commit) => <CommitRow key={commit.id} owner={owner} repo={repo} commit={commit} />)
            )}
          </div>
        )}
      </div>
    </div>
  );
}
