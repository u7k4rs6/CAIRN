import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { DiffView } from "@/components/DiffView";
import { NotFoundState } from "@/components/NotFoundState";
import { Avatar } from "@/components/ui/Avatar";

export default async function CommitPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; sha: string }>;
}) {
  const { owner, repo, sha } = await params;

  try {
    const { commit, diffs } = await api.commitDiff(owner, repo, sha);
    const date = new Date(commit.authorTime * 1000).toISOString().slice(0, 10);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-4">
          <div className="flex items-start gap-3">
            <Avatar username={commit.authorName} size={28} />
            <div className="min-w-0">
              <h1 className="font-medium text-ink whitespace-pre-wrap">{commit.message.split("\n")[0]}</h1>
              <p className="text-ink-muted text-xs mt-1">
                <span className="font-mono">{commit.id.slice(0, 12)}</span> &middot; {commit.authorName}{" "}
                <span className="font-mono">&lt;{commit.authorEmail}&gt;</span> &middot; <span className="font-mono">{date}</span>
              </p>
            </div>
          </div>
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
