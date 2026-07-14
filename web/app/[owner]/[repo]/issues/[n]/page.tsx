import { api } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";
import { AuthBar } from "@/components/AuthBar";
import { CommentComposer } from "@/components/CommentComposer";
import { SidebarMeta } from "@/components/SidebarMeta";

export default async function IssueDetailPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; n: string }>;
}) {
  const { owner, repo, n } = await params;
  const issue = await api.issue(owner, repo, n);

  if (!issue) {
    return <NotFoundState label="issue" />;
  }

  return (
    <div>
      <RepoHeader owner={owner} repo={repo} active="issues" />
      <div className="px-4 py-4 max-w-4xl mx-auto flex gap-4 items-start">
        <div className="flex-1 min-w-0 flex flex-col gap-4">
          <div>
            <h1 className="text-lg font-display font-bold text-ink">
              {issue.title} <span className="text-ink-muted font-normal font-sans">#{issue.id}</span>
            </h1>
            <span className={`state-chip mt-1 ${issue.state === "OPEN" ? "bg-veg/15 text-veg" : "bg-ink-muted/15 text-ink-2"}`}>
              {issue.state.toLowerCase()}
            </span>
          </div>
          <div className="border border-hairline rounded p-3 text-sm whitespace-pre-wrap bg-surface text-ink">{issue.body}</div>
          <AuthBar />
          <CommentComposer owner={owner} repo={repo} issueId={issue.id} />
        </div>
        <SidebarMeta owner={owner} repo={repo} issue={issue} />
      </div>
    </div>
  );
}
