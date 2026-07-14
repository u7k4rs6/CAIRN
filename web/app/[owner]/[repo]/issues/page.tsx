import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";
import { LabelChip } from "@/components/LabelChip";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { CairnMark } from "@/components/CairnMark";

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
            <Select name="state" defaultValue={filters.state || ""}>
              <option value="">All states</option>
              <option value="OPEN">Open</option>
              <option value="CLOSED">Closed</option>
            </Select>
            <Input name="label" defaultValue={filters.label || ""} placeholder="label" className="w-28 font-mono" />
            <Input name="assignee" defaultValue={filters.assignee || ""} placeholder="assignee" className="w-28 font-mono" />
            <Button type="submit" variant="primary" className="py-1">
              Filter
            </Button>
            {(filters.state || filters.label || filters.assignee) && (
              <Link href={`/${owner}/${repo}/issues`} className="text-ink-muted hover:text-route hover:underline">
                Clear
              </Link>
            )}
          </form>

          {issues.length === 0 ? (
            <div className="contour-bg border border-hairline rounded p-8 text-center flex flex-col items-center gap-3">
              <CairnMark size={28} className="text-ink-muted" />
              <p className="text-ink-2 text-sm">No issues match. Open one to start tracking work.</p>
            </div>
          ) : (
            <div className="border border-hairline rounded overflow-hidden">
              {issues.map((issue) => (
                <div key={issue.id} className="flex items-center justify-between px-3 py-2 border-b border-hairline last:border-0 text-sm gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <Link href={`/${owner}/${repo}/issues/${issue.id}`} className="font-medium text-ink hover:text-route hover:underline truncate">
                      {issue.title}
                    </Link>
                    {issue.labels.map((l) => (
                      <LabelChip key={l.id} label={l} />
                    ))}
                  </div>
                  <span className="text-ink-muted text-xs shrink-0 font-mono">
                    #{issue.id} opened by {issue.author?.username}
                    {issue.assignees.length > 0 && <> &middot; {issue.assignees.map((a) => a.username).join(", ")}</>}
                    {issue.milestone && <> &middot; {issue.milestone.title}</>}
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
