import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";
import { LabelChip } from "@/components/LabelChip";

/** Frontend spec, section 5.7: list has a FilterBar (open/closed, label, assignee) and IssueRow. */
export default async function IssuesPage({
  params,
  searchParams,
}: {
  params: Promise<{ owner: string; repo: string }>;
  searchParams: Promise<{ state?: string; label?: string; assignee?: string }>;
}) {
  const { owner, repo } = await params;
  const filters = await searchParams;

  try {
    const issues = await api.issues(owner, repo, filters);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="issues" />
        <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-3">
          <form className="flex flex-wrap items-center gap-2 text-sm">
            <select name="state" defaultValue={filters.state || ""} className="border border-border rounded px-2 py-1 bg-bg-subtle">
              <option value="">All states</option>
              <option value="OPEN">Open</option>
              <option value="CLOSED">Closed</option>
            </select>
            <input
              name="label"
              defaultValue={filters.label || ""}
              placeholder="label"
              className="border border-border rounded px-2 py-1 bg-bg-subtle w-28"
            />
            <input
              name="assignee"
              defaultValue={filters.assignee || ""}
              placeholder="assignee"
              className="border border-border rounded px-2 py-1 bg-bg-subtle w-28"
            />
            <button type="submit" className="bg-accent text-accent-fg rounded px-3 py-1 font-medium">
              Filter
            </button>
            {(filters.state || filters.label || filters.assignee) && (
              <Link href={`/${owner}/${repo}/issues`} className="text-fg-muted hover:underline">
                Clear
              </Link>
            )}
          </form>

          {issues.length === 0 ? (
            <p className="text-fg-muted text-sm py-8">No issues match.</p>
          ) : (
            <div className="border border-border rounded overflow-hidden">
              {issues.map((issue) => (
                <div key={issue.id} className="flex items-center justify-between px-3 py-2 border-b border-border last:border-0 text-sm gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <Link href={`/${owner}/${repo}/issues/${issue.id}`} className="font-medium hover:underline truncate">
                      {issue.title}
                    </Link>
                    {issue.labels.map((l) => (
                      <LabelChip key={l.id} label={l} />
                    ))}
                  </div>
                  <span className="text-fg-muted text-xs shrink-0">
                    #{issue.id} opened by {issue.author?.username}
                    {issue.assignees.length > 0 && <> · {issue.assignees.map((a) => a.username).join(", ")}</>}
                    {issue.milestone && <> · {issue.milestone.title}</>}
                  </span>
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
