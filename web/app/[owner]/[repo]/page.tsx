import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { FileTreeView } from "@/components/FileTreeView";
import { NotFoundState } from "@/components/NotFoundState";

const DEFAULT_REF = "main";

export default async function RepoHomePage({
  params,
}: {
  params: Promise<{ owner: string; repo: string }>;
}) {
  const { owner, repo } = await params;

  try {
    const entries = await api.tree(owner, repo, DEFAULT_REF, []);
    const readme = entries.find((e) => e.kind === "blob" && /^readme(\.md)?$/i.test(e.name));
    const readmeContent = readme ? await api.blob(owner, repo, DEFAULT_REF, [readme.name]) : null;

    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          {entries.length === 0 ? (
            <div className="border border-border rounded p-6 text-sm text-fg-muted">
              This repository is empty. Push some code to get started:
              <pre className="bg-bg-subtle mt-2 p-3 rounded font-mono-data text-xs overflow-x-auto">
{`git remote add origin http://localhost:8080/${owner}/${repo}.git
git push origin main`}
              </pre>
            </div>
          ) : (
            <div className="border border-border rounded overflow-hidden mb-4">
              <FileTreeView owner={owner} repo={repo} gitRef={DEFAULT_REF} path={[]} entries={entries} />
            </div>
          )}
          {readmeContent && (
            <div className="border border-border rounded p-4">
              <pre className="whitespace-pre-wrap font-mono-data text-sm">{readmeContent.content}</pre>
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
