import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { FileTreeView } from "@/components/FileTreeView";
import { NotFoundState } from "@/components/NotFoundState";

export default async function TreePage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; ref: string; path?: string[] }>;
}) {
  const { owner, repo, ref, path = [] } = await params;

  try {
    const entries = await api.tree(owner, repo, ref, path);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <div className="text-sm text-fg-muted mb-2 font-mono-data">
            {ref} / {path.join("/") || "."}
          </div>
          <div className="border border-border rounded overflow-hidden">
            <FileTreeView owner={owner} repo={repo} gitRef={ref} path={path} entries={entries} />
          </div>
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="path" />;
    }
    throw e;
  }
}
