import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";

export default async function IssuesPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string }>;
}) {
  const { owner, repo } = await params;

  try {
    const issues = await api.issues(owner, repo);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="issues" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          {issues.length === 0 ? (
            <p className="text-fg-muted text-sm py-8">No issues yet.</p>
          ) : (
            <div className="border border-border rounded overflow-hidden">
              {issues.map((issue) => (
                <div key={issue.id} className="flex items-center justify-between px-3 py-2 border-b border-border last:border-0 text-sm">
                  <Link href={`/${owner}/${repo}/issues/${issue.id}`} className="font-medium hover:underline">
                    {issue.title}
                  </Link>
                  <span className="text-fg-muted text-xs">
                    #{issue.id} opened by {issue.author?.username}
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
