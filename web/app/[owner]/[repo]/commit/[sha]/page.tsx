import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { DiffView } from "@/components/DiffView";
import { NotFoundState } from "@/components/NotFoundState";

export default async function CommitPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; sha: string }>;
}) {
  const { owner, repo, sha } = await params;

  try {
    const { commit, diffs } = await api.commitDiff(owner, repo, sha);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <h1 className="font-medium mb-1">{commit.message.split("\n")[0]}</h1>
          <p className="text-fg-muted text-xs font-mono-data mb-4">
            {commit.id.slice(0, 12)} by {commit.authorName} &lt;{commit.authorEmail}&gt;
          </p>
          <DiffView diffs={diffs} />
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="commit" />;
    }
    throw e;
  }
}
