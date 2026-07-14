import Link from "next/link";
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
        <RepoHeader owner={owner} repo={repo} active="code" gitRef={ref} />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <div className="text-sm text-ink-muted mb-2 font-mono">
            <Link href={`/${owner}/${repo}`} className="hover:text-route hover:underline">
              {repo}
            </Link>
            {path.map((segment, i) => (
              <span key={i}>
                <span className="mx-1 text-contour">&#9656;</span>
                {i === path.length - 1 ? (
                  <span className="text-ink">{segment}</span>
                ) : (
                  <Link
                    href={`/${owner}/${repo}/tree/${ref}/${path.slice(0, i + 1).join("/")}`}
                    className="hover:text-route hover:underline"
                  >
                    {segment}
                  </Link>
                )}
              </span>
            ))}
          </div>
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
