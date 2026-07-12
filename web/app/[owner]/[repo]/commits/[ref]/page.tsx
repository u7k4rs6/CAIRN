import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { CommitRow } from "@/components/CommitRow";
import { NotFoundState } from "@/components/NotFoundState";

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
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <h1 className="text-sm font-medium mb-2">Commits on {ref}</h1>
          {commits.length === 0 ? (
            <p className="text-fg-muted text-sm py-8">No commits yet.</p>
          ) : (
            <div className="border border-border rounded overflow-hidden">
              {commits.map((c) => (
                <CommitRow key={c.id} owner={owner} repo={repo} commit={c} />
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
