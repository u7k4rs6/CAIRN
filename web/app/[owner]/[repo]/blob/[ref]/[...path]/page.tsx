import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";
import { CodeViewer } from "@/components/CodeViewer";
import { highlightLine, languageForPath } from "@/lib/highlight";

export default async function BlobPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; ref: string; path: string[] }>;
}) {
  const { owner, repo, ref, path } = await params;
  const joinedPath = path.join("/");

  try {
    const blob = await api.blob(owner, repo, ref, path);
    const language = languageForPath(joinedPath);
    const lines = blob.content.split("\n").map((line, i) => ({
      lineNumber: i + 1,
      html: highlightLine(line, language),
    }));

    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <div className="text-sm text-fg-muted mb-2 font-mono-data">{joinedPath}</div>
          <CodeViewer owner={owner} repo={repo} gitRef={ref} path={joinedPath} lines={lines} />
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="file" />;
    }
    throw e;
  }
}
